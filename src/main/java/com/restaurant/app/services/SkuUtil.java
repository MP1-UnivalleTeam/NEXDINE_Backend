package com.restaurant.app.services;

import java.text.Normalizer;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public final class SkuUtil {

    private SkuUtil() {
    }

    public static final int MAX_INICIALES = 5;

    public static final int DIGITOS_CONSECUTIVO = 6;

    public static final int DIGITOS_CODIGO = 3;

    public static final String SEPARADOR = "-";

    public static final String PREFIJO_CODIGO = "R";

    public static final String INICIALES_POR_DEFECTO = "GEN";

    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^A-Z0-9]+");

    private static final Pattern DIACRITICOS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    public static String generarIniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return INICIALES_POR_DEFECTO;
        }

        String sinTildes = DIACRITICOS.matcher(
                Normalizer.normalize(nombre, Normalizer.Form.NFD)).replaceAll("");

        String mayusculas = sinTildes.toUpperCase(Locale.ROOT);

        String[] palabras = NO_ALFANUMERICO.split(mayusculas);

        StringJoiner iniciales = new StringJoiner("");
        int usadas = 0;

        for (String palabra : palabras) {
            if (palabra.isEmpty()) {
                continue;
            }

            iniciales.add(palabra.substring(0, 1));

            if (++usadas >= MAX_INICIALES) {
                break;
            }
        }

        return iniciales.length() == 0 ? INICIALES_POR_DEFECTO : iniciales.toString();
    }

    public static String formatearCodigo(long numero) {
        if (numero <= 0) {
            throw new IllegalArgumentException(
                    "El número de secuencia del código de restaurante debe ser mayor que cero.");
        }

        return PREFIJO_CODIGO + completarConCeros(numero);
    }

    private static String completarConCeros(long valor) {
        StringBuilder sb = new StringBuilder(Long.toString(valor));
        while (sb.length() < DIGITOS_CODIGO) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    public static String formatearConsecutivo(long consecutivo) {
        if (consecutivo <= 0) {
            throw new IllegalArgumentException("El consecutivo del producto debe ser mayor que cero.");
        }

        String digitos = Long.toString(consecutivo);
        StringBuilder sb = new StringBuilder(digitos);
        while (sb.length() < DIGITOS_CONSECUTIVO) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    public static String construirSku(String iniciales, String codigo, long consecutivo) {
        if (iniciales == null || iniciales.isBlank()) {
            throw new IllegalArgumentException("Las iniciales del restaurante son obligatorias.");
        }

        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código del restaurante es obligatorio.");
        }

        return iniciales.trim() + SEPARADOR
                + codigo.trim().toUpperCase(Locale.ROOT) + SEPARADOR
                + formatearConsecutivo(consecutivo);
    }

    public static String normalizarNombre(String nombre) {
        if (nombre == null) {
            return null;
        }

        String limpio = nombre.trim();

        if (limpio.isEmpty()) {
            return null;
        }

        String sinTildes = DIACRITICOS.matcher(
                Normalizer.normalize(limpio, Normalizer.Form.NFD)).replaceAll("");

        String minusculas = sinTildes.toLowerCase(Locale.ROOT);

        String sinSimbolos = minusculas.replaceAll("[^a-z0-9\\s]", " ");

        return ESPACIOS.matcher(sinSimbolos).replaceAll(" ").trim();
    }

    public static Long extraerConsecutivo(String sku) {
        if (sku == null) {
            return null;
        }

        int ultimo = sku.lastIndexOf(SEPARADOR);

        if (ultimo < 0 || ultimo == sku.length() - 1) {
            return null;
        }

        try {
            return Long.parseLong(sku.substring(ultimo + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
