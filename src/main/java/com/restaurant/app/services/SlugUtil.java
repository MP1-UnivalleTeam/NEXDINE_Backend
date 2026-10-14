package com.restaurant.app.services;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtil {

    private SlugUtil() {
    }

    public static String slugify(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        normalizado = normalizado.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        normalizado = normalizado.toLowerCase(Locale.ROOT);

        normalizado = normalizado.replaceAll("[^a-z0-9]+", "-");

        normalizado = normalizado.replaceAll("^-+", "").replaceAll("-+$", "");

        return normalizado;
    }
}
