package com.xfusion.fusiondesk.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

public final class DedupKeyGenerator {
    private DedupKeyGenerator() { }

    public static String generate(String submitter, String title, String description) {
        String source = normalize(submitter) + "\n" + normalize(title) + "\n" + normalize(description);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
