package com.liskovsoft.smartyoutubetv2.common.misc;

import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort client-side language filter for YouTube search results,
 * since YouTube's search API does not expose a native language filter.
 */
public class SearchLanguageFilter {
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}']+");
    private static final Map<String, Set<String>> STOPWORDS = new HashMap<>();

    static {
        STOPWORDS.put("fr", new HashSet<>(Arrays.asList(
                "le", "la", "les", "des", "un", "une", "de", "du", "au", "aux", "et", "en",
                "est", "sont", "pour", "dans", "avec", "sur", "pas", "qui", "que", "quoi",
                "comment", "pourquoi", "quand", "mais", "ou", "donc", "vous", "nous", "ils",
                "elles", "je", "tu", "il", "elle", "on", "ce", "se", "ne", "si", "mon", "ma",
                "mes", "ton", "son", "sa", "ses", "nos", "vos", "cette", "ces", "tout", "tous",
                "etre", "avoir", "faire", "plus", "moins", "sans", "chez", "entre"
        )));
        STOPWORDS.put("en", new HashSet<>(Arrays.asList(
                "the", "and", "for", "with", "that", "this", "from", "you", "are", "to", "in",
                "of", "on", "is", "it", "my", "we", "all", "no", "so", "do", "if", "up", "out",
                "at", "by", "as", "or", "an", "be", "can", "will", "just", "was", "were",
                "what", "when", "where", "why", "how", "who", "which", "have", "has", "had",
                "not", "but", "they", "their", "your", "about", "into", "over", "after",
                "best", "new", "make", "like", "your", "here", "there"
        )));
        STOPWORDS.put("es", new HashSet<>(Arrays.asList(
                "el", "los", "las", "una", "para", "con", "por", "como", "que",
                "pero", "más", "mas", "este", "esta", "esto", "todo", "todos",
                "sobre", "entre", "cuando", "donde", "quien", "porque", "muy", "sin"
        )));
        STOPWORDS.put("de", new HashSet<>(Arrays.asList(
                "der", "die", "das", "und", "für", "mit", "wie", "warum", "ein",
                "eine", "einen", "einem", "einer", "nicht", "ist", "sind", "war",
                "von", "auf", "aus", "bei", "nach", "über", "unter", "auch", "noch"
        )));
        STOPWORDS.put("it", new HashSet<>(Arrays.asList(
                "il", "lo", "gli", "una", "per", "con", "come", "che", "non",
                "sono", "della", "delle", "dello", "degli", "questo", "questa",
                "tutto", "tutti", "anche", "più", "come", "perché", "dove"
        )));
        STOPWORDS.put("pt", new HashSet<>(Arrays.asList(
                "os", "as", "uma", "para", "com", "por", "como", "que", "não",
                "nao", "mais", "este", "esta", "isso", "tudo", "todos", "sobre",
                "entre", "quando", "onde", "quem", "porque", "muito", "sem"
        )));
    }

    private SearchLanguageFilter() {
    }

    public static void filter(VideoGroup group, String targetLang) {
        if (group == null || group.isEmpty() || TextUtils.isEmpty(targetLang)) {
            return;
        }

        List<Video> snapshot = new ArrayList<>(group.getVideos());
        for (Video video : snapshot) {
            if (!matchesLanguage(video, targetLang)) {
                group.remove(video);
            }
        }
    }

    private static boolean matchesLanguage(Video video, String targetLang) {
        String text = buildSampleText(video);
        if (text.isEmpty()) {
            return true;
        }

        switch (targetLang) {
            case "ar":
                return containsScript(text, Character.UnicodeBlock.ARABIC);
            case "ja":
                return containsJapaneseScript(text);
            case "ko":
                return containsScript(text, Character.UnicodeBlock.HANGUL_SYLLABLES,
                        Character.UnicodeBlock.HANGUL_JAMO, Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO);
            case "zh":
                return containsScript(text, Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                        && !containsJapaneseKana(text);
            case "ru":
                return containsScript(text, Character.UnicodeBlock.CYRILLIC);
            default:
                return matchesLatinLanguage(text, targetLang);
        }
    }

    private static String buildSampleText(Video video) {
        StringBuilder sb = new StringBuilder();
        if (video.title != null) {
            sb.append(video.title);
        }
        if (video.description != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(video.description);
        }
        return sb.toString().trim();
    }

    private static boolean matchesLatinLanguage(String text, String targetLang) {
        // Reject predominantly non-Latin scripts
        int latinCount = 0;
        int nonLatinLetterCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            if (c <= 0x024F) {
                latinCount++;
            } else {
                nonLatinLetterCount++;
            }
        }
        if (nonLatinLetterCount > latinCount) {
            return false;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new HashMap<>();
        for (String lang : STOPWORDS.keySet()) {
            scores.put(lang, 0);
        }

        Matcher matcher = WORD_PATTERN.matcher(lower);
        while (matcher.find()) {
            String word = matcher.group();
            for (Map.Entry<String, Set<String>> entry : STOPWORDS.entrySet()) {
                if (entry.getValue().contains(word)) {
                    scores.put(entry.getKey(), scores.get(entry.getKey()) + 1);
                }
            }
        }

        // Diacritic hints
        if (containsAny(lower, 'é', 'è', 'ê', 'à', 'ç', 'ù', 'î', 'ô')) {
            scores.put("fr", scores.get("fr") + 2);
        }
        if (containsAny(lower, 'ñ', '¿', '¡')) {
            scores.put("es", scores.get("es") + 3);
        }
        if (containsAny(lower, 'ß', 'ä', 'ö', 'ü')) {
            scores.put("de", scores.get("de") + 2);
        }
        if (containsAny(lower, 'ã', 'õ')) {
            scores.put("pt", scores.get("pt") + 3);
        }

        int targetScore = scores.containsKey(targetLang) ? scores.get(targetLang) : 0;
        int bestOtherScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (!entry.getKey().equals(targetLang) && entry.getValue() > bestOtherScore) {
                bestOtherScore = entry.getValue();
            }
        }

        // When filtering for a specific language, require positive signal unless the title is very short (<= 2 words)
        if (targetScore > 0) {
            return targetScore >= bestOtherScore;
        }
        if (bestOtherScore > 0) {
            return false;
        }
        String[] words = lower.trim().split("\\s+");
        return words.length <= 2;
    }

    private static boolean containsAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            for (char target : chars) {
                if (c == target) return true;
            }
        }
        return false;
    }

    private static boolean containsJapaneseKana(String text) {
        return containsScript(text, Character.UnicodeBlock.HIRAGANA, Character.UnicodeBlock.KATAKANA);
    }

    private static boolean containsJapaneseScript(String text) {
        return containsScript(text, Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA, Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
    }

    private static boolean containsScript(String text, Character.UnicodeBlock... blocks) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            for (Character.UnicodeBlock target : blocks) {
                if (block == target) {
                    return true;
                }
            }
        }
        return false;
    }
}
