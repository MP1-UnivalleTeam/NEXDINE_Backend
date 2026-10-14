package com.restaurant.app.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Component
public class ImagenProductoValidador {

    private static final Logger log = LoggerFactory.getLogger(ImagenProductoValidador.class);

    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;

    @Value("${APP_IMAGEN_MAX_BYTES:5242880}")
    private long maxBytes = DEFAULT_MAX_BYTES;

    public long getMaxBytes() {
        return maxBytes;
    }

    public static String formatosSoportados() {
        return "JPEG, PNG o WebP";
    }

    public TipoImagen validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ImagenInvalidaException(
                    "No se recibio ningun archivo de imagen. El archivo enviado esta vacio.");
        }

        if (archivo.getSize() > maxBytes) {
            throw new ImagenDemasiadoGrandeException(archivo.getSize(), maxBytes);
        }

        byte[] cabecera = leerCabecera(archivo);
        TipoImagen tipo = detectar(cabecera);

        if (tipo == null) {
            throw new ImagenInvalidaException(
                    "El archivo enviado no es una imagen valida. Formatos aceptados: "
                            + formatosSoportados() + ".");
        }

        log.debug("NEXDINE | Imagen validada: tipo={} bytes={}", tipo, archivo.getSize());
        return tipo;
    }

    private byte[] leerCabecera(MultipartFile archivo) {
        try (InputStream in = archivo.getInputStream()) {
            byte[] buffer = new byte[12];
            int leidos = in.read(buffer);
            if (leidos <= 0) {
                return new byte[0];
            }
            byte[] cabecera = new byte[leidos];
            System.arraycopy(buffer, 0, cabecera, 0, leidos);
            return cabecera;
        } catch (IOException e) {
            throw new ImagenInvalidaException(
                    "No fue posible leer el archivo enviado. Verifique que sea una imagen valida.");
        }
    }

    private TipoImagen detectar(byte[] b) {
        if (b == null || b.length == 0) {
            return null;
        }

        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return TipoImagen.JPEG;
        }

        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return TipoImagen.PNG;
        }

        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return TipoImagen.WEBP;
        }

        return null;
    }

    public enum TipoImagen {
        JPEG("jpg"),
        PNG("png"),
        WEBP("webp");

        private final String formato;

        TipoImagen(String formato) {
            this.formato = formato;
        }

        public String getFormato() {
            return formato;
        }

        public String getContentType() {
            return switch (this) {
                case JPEG -> "image/jpeg";
                case PNG -> "image/png";
                case WEBP -> "image/webp";
            };
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static class ImagenInvalidaException extends RuntimeException {
        public ImagenInvalidaException(String message) {
            super(message);
        }
    }

    public static class ImagenDemasiadoGrandeException extends RuntimeException {
        private final long tamanho;
        private final long maximo;

        public ImagenDemasiadoGrandeException(long tamanho, long maximo) {
            super("La imagen supera el tamano maximo permitido de "
                    + formatear(maximo) + ". Archivo recibido: " + formatear(tamanho) + ".");
            this.tamanho = tamanho;
            this.maximo = maximo;
        }

        public long getTamanho() {
            return tamanho;
        }

        public long getMaximo() {
            return maximo;
        }

        private static String formatear(long bytes) {
            if (bytes >= 1024 * 1024) {
                return (bytes / (1024.0 * 1024.0)) + " MB";
            }
            if (bytes >= 1024) {
                return (bytes / 1024.0) + " KB";
            }
            return bytes + " bytes";
        }
    }
}
