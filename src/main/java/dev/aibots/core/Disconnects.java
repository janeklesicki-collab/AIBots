package dev.aibots.core;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wyciąganie czytelnego powodu wyrzucenia (JSON w logowaniu, NBT w konfiguracji i grze). */
final class Disconnects {
    private Disconnects() {}

    private static final Pattern JSON_TEXT = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\"");

    static String fromJson(String json) {
        Matcher m = JSON_TEXT.matcher(json);
        StringBuilder sb = new StringBuilder();
        while (m.find()) sb.append(m.group(1)).append(' ');
        return clip(sb.length() > 0 ? sb.toString() : json);
    }

    static String fromNbt(byte[] b) {
        if (b.length >= 3 && b[0] == 8) { // zwykły string
            int n = ((b[1] & 0xFF) << 8) | (b[2] & 0xFF);
            if (3 + n <= b.length) return clip(new String(b, 3, n, StandardCharsets.UTF_8));
        }
        // compound: szukamy pól "text" (typ 8, nazwa "text")
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 9 < b.length; i++) {
            if (b[i] == 8 && b[i + 1] == 0 && b[i + 2] == 4 && b[i + 3] == 't' && b[i + 4] == 'e'
                    && b[i + 5] == 'x' && b[i + 6] == 't') {
                int n = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                if (i + 9 + n <= b.length) {
                    sb.append(new String(b, i + 9, n, StandardCharsets.UTF_8)).append(' ');
                    i += 8 + n;
                }
            }
        }
        if (sb.length() > 0) return clip(sb.toString());
        return clip(new String(b, StandardCharsets.ISO_8859_1).replaceAll("[^\\x20-\\x7E]+", " "));
    }

    private static String clip(String s) {
        s = s.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }
}
