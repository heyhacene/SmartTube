package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;

public class SubtitleTranslationPrefs {
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_OPENAI = "openai";

    public static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private static final String KEY_ENABLED = "sub_ctx_enabled";
    private static final String KEY_PROVIDER = "sub_ctx_provider";
    private static final String KEY_API_KEY = "sub_ctx_api_key";
    private static final String KEY_MODEL = "sub_ctx_model";
    private static final String KEY_ENDPOINT = "sub_ctx_endpoint";
    private static final String KEY_FORCE_EN_YT_API = "sub_ctx_force_en_yt_api";
    private static final String KEY_SEARCH_LANG = "sub_ctx_search_lang";
    private static final String KEY_AUDIO_BTN_MIGRATED = "sub_ctx_audio_btn_v1";

    private static SubtitleTranslationPrefs sInstance;
    private final AppPrefs mPrefs;

    private SubtitleTranslationPrefs(Context context) {
        mPrefs = AppPrefs.instance(context.getApplicationContext());
    }

    public static synchronized SubtitleTranslationPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new SubtitleTranslationPrefs(context);
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.putBoolean(KEY_ENABLED, enabled);
    }

    public String getProvider() {
        String val = mPrefs.getString(KEY_PROVIDER, PROVIDER_GEMINI);
        return PROVIDER_OPENAI.equals(val) ? PROVIDER_OPENAI : PROVIDER_GEMINI;
    }

    public void setProvider(String provider) {
        mPrefs.putString(KEY_PROVIDER, PROVIDER_OPENAI.equals(provider) ? PROVIDER_OPENAI : PROVIDER_GEMINI);
    }

    public String getApiKey() {
        String val = mPrefs.getString(KEY_API_KEY, "");
        return val != null ? val.trim() : "";
    }

    public void setApiKey(String apiKey) {
        mPrefs.putString(KEY_API_KEY, apiKey != null ? apiKey.trim() : "");
    }

    public String getRawModel() {
        String val = mPrefs.getString(KEY_MODEL, "");
        return val != null ? val.trim() : "";
    }

    public String getEffectiveModel() {
        String raw = getRawModel();
        if (!TextUtils.isEmpty(raw)) {
            return raw;
        }
        return PROVIDER_OPENAI.equals(getProvider()) ? DEFAULT_OPENAI_MODEL : DEFAULT_GEMINI_MODEL;
    }

    public void setModel(String model) {
        mPrefs.putString(KEY_MODEL, model != null ? model.trim() : "");
    }

    public String getRawEndpoint() {
        String val = mPrefs.getString(KEY_ENDPOINT, "");
        return val != null ? val.trim() : "";
    }

    public String getEffectiveEndpoint() {
        String raw = getRawEndpoint();
        return !TextUtils.isEmpty(raw) ? raw : DEFAULT_OPENAI_ENDPOINT;
    }

    public void setEndpoint(String endpoint) {
        mPrefs.putString(KEY_ENDPOINT, endpoint != null ? endpoint.trim() : "");
    }

    public boolean isConfigured() {
        if (!isEnabled()) {
            return false;
        }
        if (!TextUtils.isEmpty(getApiKey())) {
            return true;
        }
        // Allow local OpenAI-compatible servers (e.g. Ollama / LM Studio) without an API key
        return PROVIDER_OPENAI.equals(getProvider()) && !TextUtils.isEmpty(getRawEndpoint());
    }

    public boolean isForceEnglishYouTubeApi() {
        return mPrefs.getBoolean(KEY_FORCE_EN_YT_API, false);
    }

    public void setForceEnglishYouTubeApi(boolean enabled) {
        mPrefs.putBoolean(KEY_FORCE_EN_YT_API, enabled);
    }

    public void applyYouTubeApiLanguage() {
        if (isForceEnglishYouTubeApi()) {
            LocaleManager.instance().setLanguage("en");
        } else {
            LocaleManager.unhold();
        }
    }

    public String getSearchLanguage() {
        String val = mPrefs.getString(KEY_SEARCH_LANG, "");
        return val != null ? val.trim() : "";
    }

    public void setSearchLanguage(String langCode) {
        mPrefs.putString(KEY_SEARCH_LANG, langCode != null ? langCode.trim() : "");
    }

    public boolean isAudioTrackButtonMigrated() {
        return mPrefs.getBoolean(KEY_AUDIO_BTN_MIGRATED, false);
    }

    public void setAudioTrackButtonMigrated(boolean migrated) {
        mPrefs.putBoolean(KEY_AUDIO_BTN_MIGRATED, migrated);
    }
}
