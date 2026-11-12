package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.text.TextUtils;

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
        return "true".equals(mPrefs.getProfileData(KEY_ENABLED));
    }

    public void setEnabled(boolean enabled) {
        mPrefs.setProfileData(KEY_ENABLED, enabled ? "true" : "false");
    }

    public String getProvider() {
        String val = mPrefs.getProfileData(KEY_PROVIDER);
        return PROVIDER_OPENAI.equals(val) ? PROVIDER_OPENAI : PROVIDER_GEMINI;
    }

    public void setProvider(String provider) {
        mPrefs.setProfileData(KEY_PROVIDER, PROVIDER_OPENAI.equals(provider) ? PROVIDER_OPENAI : PROVIDER_GEMINI);
    }

    public String getApiKey() {
        String val = mPrefs.getProfileData(KEY_API_KEY);
        return val != null ? val.trim() : "";
    }

    public void setApiKey(String apiKey) {
        mPrefs.setProfileData(KEY_API_KEY, apiKey != null ? apiKey.trim() : "");
    }

    public String getRawModel() {
        String val = mPrefs.getProfileData(KEY_MODEL);
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
        mPrefs.setProfileData(KEY_MODEL, model != null ? model.trim() : "");
    }

    public String getRawEndpoint() {
        String val = mPrefs.getProfileData(KEY_ENDPOINT);
        return val != null ? val.trim() : "";
    }

    public String getEffectiveEndpoint() {
        String raw = getRawEndpoint();
        return !TextUtils.isEmpty(raw) ? raw : DEFAULT_OPENAI_ENDPOINT;
    }

    public void setEndpoint(String endpoint) {
        mPrefs.setProfileData(KEY_ENDPOINT, endpoint != null ? endpoint.trim() : "");
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
}
