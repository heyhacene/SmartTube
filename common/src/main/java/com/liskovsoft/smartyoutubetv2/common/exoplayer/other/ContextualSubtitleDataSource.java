package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.SubtitleTranslationPrefs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Intercepts YouTube translated subtitle requests (timedtext?tlang=...),
 * fetches the original subtitle track, and translates it contextually via LLM.
 * Falls back transparently to YouTube's built-in translation if disabled or on error.
 */
public class ContextualSubtitleDataSource implements DataSource {
    private static final String TAG = ContextualSubtitleDataSource.class.getSimpleName();
    private static final int CACHE_SIZE = 16;
    private static final LruCache<String, byte[]> sCache = new LruCache<>(CACHE_SIZE);

    private final Context mContext;
    private final DataSource mDelegate;

    private byte[] mBuffer;
    private int mReadPosition;
    private int mBytesRemaining;
    private Uri mActiveUri;

    public static class Factory implements DataSource.Factory {
        private final Context mContext;
        private final DataSource.Factory mDelegateFactory;

        public Factory(Context context, DataSource.Factory delegateFactory) {
            mContext = context.getApplicationContext();
            mDelegateFactory = delegateFactory;
        }

        @Override
        public DataSource createDataSource() {
            return new ContextualSubtitleDataSource(mContext, mDelegateFactory.createDataSource());
        }
    }

    public ContextualSubtitleDataSource(Context context, DataSource delegate) {
        mContext = context.getApplicationContext();
        mDelegate = delegate;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        mDelegate.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        resetBuffer();

        Uri uri = dataSpec.uri;
        SubtitleTranslationPrefs prefs = SubtitleTranslationPrefs.instance(mContext);

        if (prefs.isConfigured() && isTranslatedTimedTextUri(uri)) {
            String targetLang = uri.getQueryParameter("tlang");
            Uri originUri = stripQueryParameter(uri, "tlang");
            String cacheKey = buildCacheKey(originUri, targetLang, prefs);

            byte[] translatedBytes;
            synchronized (sCache) {
                translatedBytes = sCache.get(cacheKey);
            }

            if (translatedBytes == null) {
                try {
                    byte[] originBytes = fetchAllBytes(originUri);
                    translatedBytes = ContextualSubtitleTranslator.translate(originBytes, targetLang, prefs);
                    if (translatedBytes != null && translatedBytes.length > 0) {
                        synchronized (sCache) {
                            sCache.put(cacheKey, translatedBytes);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Contextual translation failed, falling back to YouTube tlang: " + e.getMessage());
                    translatedBytes = null;
                }
            }

            if (translatedBytes != null && translatedBytes.length > 0) {
                mBuffer = translatedBytes;
                mActiveUri = uri;
                int startPos = (int) Math.min(dataSpec.position, mBuffer.length);
                mReadPosition = startPos;
                mBytesRemaining = mBuffer.length - startPos;
                if (dataSpec.length != C.LENGTH_UNSET) {
                    mBytesRemaining = (int) Math.min(mBytesRemaining, dataSpec.length);
                }
                return mBytesRemaining;
            }
        }

        return mDelegate.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (mBuffer != null) {
            if (readLength == 0) {
                return 0;
            }
            if (mBytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            int bytesToRead = Math.min(readLength, mBytesRemaining);
            System.arraycopy(mBuffer, mReadPosition, buffer, offset, bytesToRead);
            mReadPosition += bytesToRead;
            mBytesRemaining -= bytesToRead;
            return bytesToRead;
        }
        return mDelegate.read(buffer, offset, readLength);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return mBuffer != null ? mActiveUri : mDelegate.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return mBuffer != null ? java.util.Collections.emptyMap() : mDelegate.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        if (mBuffer != null) {
            resetBuffer();
            return;
        }
        mDelegate.close();
    }

    private void resetBuffer() {
        mBuffer = null;
        mReadPosition = 0;
        mBytesRemaining = 0;
        mActiveUri = null;
    }

    private byte[] fetchAllBytes(Uri uri) throws IOException {
        DataSpec spec = new DataSpec(uri);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            mDelegate.open(spec);
            int read;
            while ((read = mDelegate.read(chunk, 0, chunk.length)) != C.RESULT_END_OF_INPUT) {
                if (read > 0) {
                    baos.write(chunk, 0, read);
                }
            }
            return baos.toByteArray();
        } finally {
            try {
                mDelegate.close();
            } catch (IOException ignored) {
                // Best-effort close on delegate
            }
        }
    }

    private static boolean isTranslatedTimedTextUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        String path = uri.getPath();
        if (path == null || !path.contains("timedtext")) {
            return false;
        }
        String tlang = uri.getQueryParameter("tlang");
        return !TextUtils.isEmpty(tlang);
    }

    private static Uri stripQueryParameter(Uri uri, String paramToRemove) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        Set<String> names = uri.getQueryParameterNames();
        for (String name : names) {
            if (paramToRemove.equals(name)) {
                continue;
            }
            for (String value : uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value);
            }
        }
        return builder.build();
    }

    private static String buildCacheKey(Uri originUri, String targetLang, SubtitleTranslationPrefs prefs) {
        String videoId = originUri.getQueryParameter("v");
        String srcLang = originUri.getQueryParameter("lang");
        String base = !TextUtils.isEmpty(videoId) ? (videoId + ":" + srcLang) : originUri.toString();
        return base + "|" + targetLang + "|" + prefs.getProvider() + "|" + prefs.getEffectiveModel();
    }
}
