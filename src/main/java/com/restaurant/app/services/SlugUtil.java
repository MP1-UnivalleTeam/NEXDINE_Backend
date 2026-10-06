package com.restaurant.app.services;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Genera slugs URL-safe a partir de nombres de restaurante.
 *
 * Reglas:
 *  · Normaliza tildes y diacríticos: "Café Central" → "cafe-central"
 *  · Reemplaza separadores por guion: "Pizza House" → "pizza-house"
 *  · Minúsculas, solo [a-z0-9-]
 */
public final class SlugUtil {

    private SlugUtil() {
        // utility class
    }

    public static String slugify(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        // Eliminar diacríticos: "café" → "cafe"
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        normalizado = normalizado.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Minúsculas y sin acentos
        normalizado = normalizado.toLowerCase(Locale.ROOT);

        // Cualquier secuencia no alfanumérica pasa a guion simple
        normalizado = normalizado.replaceAll("[^a-z0-9]+", "-");

        // Recortar guiones al inicio y al final
        normalizado = normalizado.replaceAll("^-+", "").replaceAll("-+$", "");

        return normalizado;
    }
}