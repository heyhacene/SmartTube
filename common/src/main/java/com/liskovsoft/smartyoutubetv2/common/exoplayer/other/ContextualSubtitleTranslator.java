package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.text.TextUtils;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SubtitleTranslationPrefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Translates WebVTT or TTML/XML subtitle files in contextual batches using an LLM
 * so conversational phrases, idioms, and proper nouns are preserved accurately.
 */
public class ContextualSubtitleTranslator {
    private static final String TAG = ContextualSubtitleTranslator.class.getSimpleName();
    private static final int BATCH_SIZE = 35;
    private static final int CONTEXT_WINDOW = 5;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final Pattern VTT_INLINE_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern XML_CUE_PATTERN = Pattern.compile(
            "(<(?:p|text)\\b[^>]*>)(.*?)(</(?:p|text)>)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private ContextualSubtitleTranslator() {
    }

    public static byte[] translate(byte[] rawBytes, String targetLang, SubtitleTranslationPrefs prefs) throws IOException {
        if (rawBytes == null || rawBytes.length == 0 || TextUtils.isEmpty(targetLang)) {
            return null;
        }

        String content = new String(rawBytes, UTF_8);
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        OkHttpClient client = OkHttpManager.instance().getClient()
                .newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        if (trimmed.startsWith("WEBVTT") || trimmed.contains("-->")) {
            String translatedVtt = translateWebVtt(content, targetLang, prefs, client);
            return translatedVtt != null ? translatedVtt.getBytes(UTF_8) : null;
        }

        if (trimmed.startsWith("<")) {
            String translatedXml = translateXmlSubtitles(content, targetLang, prefs, client);
            return translatedXml != null ? translatedXml.getBytes(UTF_8) : null;
        }

        return null;
    }

    private static String translateWebVtt(String content, String targetLang,
                                          SubtitleTranslationPrefs prefs, OkHttpClient client) {
        String normalized = content.replace("\r\n", "\n");
        String[] blocks = normalized.split("\n\n+");

        List<Integer> cueBlockIndices = new ArrayList<>();
        List<String> cueTimings = new ArrayList<>();
        List<String> cueTexts = new ArrayList<>();

        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i];
            String[] lines = block.split("\n");
            int timingIdx = -1;
            for (int j = 0; j < lines.length; j++) {
                if (lines[j].contains("-->")) {
                    timingIdx = j;
                    break;
                }
            }
            if (timingIdx == -1) {
                continue;
            }

            StringBuilder textBuilder = new StringBuilder();
            for (int j = timingIdx + 1; j < lines.length; j++) {
                String cleanLine = VTT_INLINE_TAG.matcher(lines[j]).replaceAll("").trim();
                if (!cleanLine.isEmpty()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(cleanLine);
                }
            }

            String plainText = textBuilder.toString().trim();
            if (plainText.isEmpty()) {
                continue;
            }

            StringBuilder headerBuilder = new StringBuilder();
            for (int j = 0; j <= timingIdx; j++) {
                if (j > 0) {
                    headerBuilder.append("\n");
                }
                headerBuilder.append(lines[j]);
            }

            cueBlockIndices.add(i);
            cueTimings.add(headerBuilder.toString());
            cueTexts.add(plainText);
        }

        if (cueTexts.isEmpty()) {
            return null;
        }

        List<String> translated = translateCueList(cueTexts, targetLang, prefs, client);
        if (translated == null || translated.size() != cueTexts.size()) {
            return null;
        }

        for (int i = 0; i < cueBlockIndices.size(); i++) {
            int blockIdx = cueBlockIndices.get(i);
            blocks[blockIdx] = cueTimings.get(i) + "\n" + translated.get(i);
        }

        return TextUtils.join("\n\n", blocks) + "\n";
    }

    private static String translateXmlSubtitles(String content, String targetLang,
                                                SubtitleTranslationPrefs prefs, OkHttpClient client) {
        Matcher matcher = XML_CUE_PATTERN.matcher(content);
        List<String> openTags = new ArrayList<>();
        List<String> closeTags = new ArrayList<>();
        List<Integer> startPositions = new ArrayList<>();
        List<Integer> endPositions = new ArrayList<>();
        List<String> cueTexts = new ArrayList<>();

        while (matcher.find()) {
            String inner = VTT_INLINE_TAG.matcher(matcher.group(2)).replaceAll("").trim();
            inner = unescapeXml(inner);
            if (inner.isEmpty()) {
                continue;
            }
            openTags.add(matcher.group(1));
            closeTags.add(matcher.group(3));
            startPositions.add(matcher.start());
            endPositions.add(matcher.end());
            cueTexts.add(inner);
        }

        if (cueTexts.isEmpty()) {
            return null;
        }

        List<String> translated = translateCueList(cueTexts, targetLang, prefs, client);
        if (translated == null || translated.size() != cueTexts.size()) {
            return null;
        }

        StringBuilder out = new StringBuilder(content.length() + 512);
        int cursor = 0;
        for (int i = 0; i < cueTexts.size(); i++) {
            out.append(content, cursor, startPositions.get(i));
            out.append(openTags.get(i));
            out.append(escapeXml(translated.get(i)));
            out.append(closeTags.get(i));
            cursor = endPositions.get(i);
        }
        out.append(content, cursor, content.length());
        return out.toString();
    }

    private static List<String> translateCueList(List<String> cues, String targetLang,
                                                 SubtitleTranslationPrefs prefs, OkHttpClient client) {
        List<String> result = new ArrayList<>(cues.size());

        for (int start = 0; start < cues.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, cues.size());
            List<String> batch = cues.subList(start, end);

            int ctxStart = Math.max(0, start - CONTEXT_WINDOW);
            List<String> ctxSource = cues.subList(ctxStart, start);
            List<String> ctxTranslated = result.subList(ctxStart, start);

            List<String> batchResult = translateBatch(batch, ctxSource, ctxTranslated, targetLang, prefs, client);
            if (batchResult == null || batchResult.size() != batch.size()) {
                Log.e(TAG, "Contextual translation batch failed at index " + start);
                return null;
            }
            result.addAll(batchResult);
        }

        return result;
    }

    private static List<String> translateBatch(List<String> batch,
                                               List<String> ctxSource,
                                               List<String> ctxTranslated,
                                               String targetLang,
                                               SubtitleTranslationPrefs prefs,
                                               OkHttpClient client) {
        try {
            String systemPrompt = buildSystemPrompt(targetLang);
            String userPrompt = buildUserPrompt(batch, ctxSource, ctxTranslated, targetLang);

            String rawJson;
            if (SubtitleTranslationPrefs.PROVIDER_OPENAI.equals(prefs.getProvider())) {
                rawJson = callOpenAiCompatible(systemPrompt, userPrompt, prefs, client);
            } else {
                rawJson = callGemini(systemPrompt, userPrompt, prefs, client);
            }

            if (TextUtils.isEmpty(rawJson)) {
                return null;
            }

            return parseTranslationsJson(rawJson, batch.size());
        } catch (Exception e) {
            Log.e(TAG, "translateBatch error: " + e.getMessage());
            return null;
        }
    }

    private static String buildSystemPrompt(String targetLang) {
        return "You are a professional subtitle translator. Translate spoken video/film dialogue into language code \"" + targetLang + "\".\n"
                + "Rules:\n"
                + "1. Use the surrounding dialogue context to resolve ambiguous short cues (e.g. \"right?\" -> \"n'est-ce pas ?\" / \"hein ?\", NOT \"droite ?\").\n"
                + "2. NEVER translate or distort proper nouns, character names, or brand names (e.g. \"Guillermo\" must stay \"Guillermo\"). If an ASR transcript slightly mangled a clearly recurring name, keep the proper name.\n"
                + "3. Keep translations concise and natural for TV subtitles.\n"
                + "4. Return ONLY a JSON object with a single key \"translations\" containing an array of strings of the EXACT same length and order as \"cues\".";
    }

    private static String buildUserPrompt(List<String> batch, List<String> ctxSource,
                                          List<String> ctxTranslated, String targetLang) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("target_language", targetLang);

        if (!ctxSource.isEmpty()) {
            JSONArray contextArr = new JSONArray();
            for (int i = 0; i < ctxSource.size(); i++) {
                JSONObject pair = new JSONObject();
                pair.put("src", ctxSource.get(i));
                pair.put("tr", ctxTranslated.get(i));
                contextArr.put(pair);
            }
            payload.put("previous_context", contextArr);
        }

        JSONArray cuesArr = new JSONArray();
        for (String cue : batch) {
            cuesArr.put(cue);
        }
        payload.put("cues", cuesArr);

        return payload.toString();
    }

    private static String callGemini(String systemPrompt, String userPrompt,
                                     SubtitleTranslationPrefs prefs, OkHttpClient client) throws IOException, JSONException {
        String model = prefs.getEffectiveModel();
        String apiKey = prefs.getApiKey();
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        JSONObject sysInstruction = new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt)));

        JSONObject userContent = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", userPrompt)));

        JSONObject generationConfig = new JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json");

        JSONObject body = new JSONObject()
                .put("system_instruction", sysInstruction)
                .put("contents", new JSONArray().put(userContent))
                .put("generationConfig", generationConfig);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_MEDIA_TYPE, body.toString()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "Gemini API HTTP " + response.code());
                return null;
            }
            JSONObject root = new JSONObject(response.body().string());
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return null;
            }
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) {
                return null;
            }
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) {
                return null;
            }
            return parts.getJSONObject(0).optString("text", null);
        }
    }

    private static String callOpenAiCompatible(String systemPrompt, String userPrompt,
                                               SubtitleTranslationPrefs prefs, OkHttpClient client) throws IOException, JSONException {
        String endpoint = prefs.getEffectiveEndpoint();
        String model = prefs.getEffectiveModel();
        String apiKey = prefs.getApiKey();

        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                .put(new JSONObject().put("role", "user").put("content", userPrompt));

        JSONObject body = new JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put("messages", messages)
                .put("response_format", new JSONObject().put("type", "json_object"));

        Request.Builder reqBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(JSON_MEDIA_TYPE, body.toString()));

        if (!TextUtils.isEmpty(apiKey)) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "OpenAI-compatible API HTTP " + response.code());
                return null;
            }
            JSONObject root = new JSONObject(response.body().string());
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            return message != null ? message.optString("content", null) : null;
        }
    }

    private static List<String> parseTranslationsJson(String rawText, int expectedSize) throws JSONException {
        String cleaned = rawText.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }

        JSONObject obj = new JSONObject(cleaned);
        JSONArray arr = obj.optJSONArray("translations");
        if (arr == null || arr.length() != expectedSize) {
            return null;
        }

        List<String> out = new ArrayList<>(expectedSize);
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.optString(i, ""));
        }
        return out;
    }

    private static String unescapeXml(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
