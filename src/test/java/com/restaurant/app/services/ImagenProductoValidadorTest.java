package com.restaurant.app.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ImagenProductoValidador - validación por contenido real")
class ImagenProductoValidadorTest {

    private @NonNull ImagenProductoValidador validador = new ImagenProductoValidador();

    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            'J', 'F', 'I', 'F', 0x00, 0x01
    };

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] WEBP = {
            'R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
    };

    @BeforeEach
    void setUp() {
        validador = new ImagenProductoValidador();
        ReflectionTestUtils.setField(validador, "maxBytes", 5L * 1024 * 1024);
    }

    @Test
    @DisplayName("Acepta un JPEG real")
    void aceptaJpeg() {
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "foto.jpg", "image/jpeg", JPEG);

        assertEquals(ImagenProductoValidador.TipoImagen.JPEG, validador.validar(archivo));
    }

    @Test
    @DisplayName("Acepta un PNG real")
    void aceptaPng() {
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "foto.png", "image/png", PNG);

        assertEquals(ImagenProductoValidador.TipoImagen.PNG, validador.validar(archivo));
    }

    @Test
    @DisplayName("Acepta un WebP real")
    void aceptaWebp() {
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "foto.webp", "image/webp", WEBP);

        assertEquals(ImagenProductoValidador.TipoImagen.WEBP, validador.validar(archivo));
    }

    @Test
    @DisplayName("RECHAZA un PDF aunque se renombre a .jpg")
    void rechazaPdfRenombrado() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>".getBytes();
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "malicioso.jpg", "image/jpeg", pdf);

        ImagenProductoValidador.ImagenInvalidaException ex = assertThrows(
                ImagenProductoValidador.ImagenInvalidaException.class,
                () -> validador.validar(archivo));

        assertEquals(true, ex.getMessage().contains("JPEG"));
    }

    @Test
    @DisplayName("RECHAZA un archivo con extensión .png pero contenido de texto")
    void rechazaExtensionFalsa() {
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "falso.png", "image/png", "esto no es una imagen".getBytes());

        assertThrows(ImagenProductoValidador.ImagenInvalidaException.class,
                () -> validador.validar(archivo));
    }

    @Test
    @DisplayName("RECHAZA un archivo vacío")
    void rechazaArchivoVacio() {
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "vacio.jpg", "image/jpeg", new byte[0]);

        assertThrows(ImagenProductoValidador.ImagenInvalidaException.class,
                () -> validador.validar(archivo));
    }

    @Test
    @DisplayName("RECHAZA null")
    void rechazaNull() {
        assertThrows(ImagenProductoValidador.ImagenInvalidaException.class,
                () -> validador.validar(null));
    }

    @Test
    @DisplayName("RECHAZA un archivo que excede el límite de 5 MB")
    void rechazaArchivoDemasiadoGrande() {
        byte[] grande = new byte[(5 * 1024 * 1024) + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);

        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "grande.png", "image/png", grande);

        ImagenProductoValidador.ImagenDemasiadoGrandeException ex = assertThrows(
                ImagenProductoValidador.ImagenDemasiadoGrandeException.class,
                () -> validador.validar(archivo));

        assertEquals(5L * 1024 * 1024, ex.getMaximo());
    }

    @Test
    @DisplayName("ACEPTA un archivo exactamente en el límite de 5 MB")
    void aceptaArchivoEnElLimite() {
        byte[] limite = new byte[5 * 1024 * 1024];
        System.arraycopy(JPEG, 0, limite, 0, JPEG.length);

        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "limite.jpg", "image/jpeg", limite);

        assertEquals(ImagenProductoValidador.TipoImagen.JPEG, validador.validar(archivo));
    }
}
