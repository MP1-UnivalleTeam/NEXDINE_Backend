package com.restaurant.app.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.restaurant.app.config.CloudinaryProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CloudinaryStorageService - subida y limpieza de recursos")
class CloudinaryStorageServiceTest {

    private @NonNull CloudinaryProperties properties = new CloudinaryProperties();
    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryStorageService storageService;

    @BeforeEach
    void setUp() {
        properties = new CloudinaryProperties();
        ReflectionTestUtils.setField(properties, "cloudName", "demo");
        ReflectionTestUtils.setField(properties, "apiKey", "1234567890");
        ReflectionTestUtils.setField(properties, "apiSecret", "secreto-de-prueba");

        cloudinary = mock(Cloudinary.class);
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);

        @SuppressWarnings("unchecked")
        ObjectProvider<Cloudinary> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cloudinary);

        storageService = new CloudinaryStorageService(properties, provider);
    }

    private MockMultipartFile imagenPng() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        return new MockMultipartFile("imagen", "foto.png", "image/png", png);
    }

    @Test
    @DisplayName("Sube a la carpeta nexdine/productos con public_id único")
    void subeConCarpetaYPublicIdUnico() throws IOException {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("secure_url", "https://res.cloudinary.com/demo/image/upload/nexdine/productos/xyz.jpg");
        respuesta.put("public_id", "nexdine/productos/producto_xyz");

        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(respuesta);

        storageService.subirImagen(imagenPng());
        storageService.subirImagen(imagenPng());

        assertTrue(storageService.estaDisponible());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
        verify(uploader, org.mockito.Mockito.times(2)).upload(any(byte[].class), captor.capture());

        Map<String, Object> opciones = captor.getAllValues().get(0);
        assertEquals("nexdine/productos", opciones.get("folder"));
        assertEquals("image", opciones.get("resource_type"));
        assertEquals(false, opciones.get("overwrite"));
        assertEquals(false, opciones.get("use_filename"));

        String id1 = (String) captor.getAllValues().get(0).get("public_id");
        String id2 = (String) captor.getAllValues().get(1).get("public_id");

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2, "Cada imagen debe tener un public_id único");
        assertTrue(id1.startsWith("producto_"));
    }

    @Test
    @DisplayName("Devuelve siempre la URL segura HTTPS")
    void devuelveUrlSegura() throws IOException {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("secure_url", "http://res.cloudinary.com/demo/image/upload/nexdine/productos/xyz.jpg");
        respuesta.put("public_id", "nexdine/productos/producto_xyz");

        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(respuesta);

        CloudinaryStorageService.ResultadoSubida resultado = storageService.subirImagen(imagenPng());

        assertTrue(resultado.url().startsWith("https://"),
                "La URL persistida debe ser HTTPS: " + resultado.url());
    }

    @Test
    @DisplayName("Error controlado si Cloudinary no devuelve secure_url")
    void errorSiNoDevuelveSecureUrl() throws IOException {
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("public_id", "nexdine/productos/producto_xyz");

        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(respuesta);

        assertThrows(CloudinaryStorageService.CloudinaryCommunicationException.class,
                () -> storageService.subirImagen(imagenPng()));
    }

    @Test
    @DisplayName("Error controlado si Cloudinary falla la comunicación")
    void errorDeComunicacion() throws IOException {
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new IOException("connection reset"));

        assertThrows(CloudinaryStorageService.CloudinaryCommunicationException.class,
                () -> storageService.subirImagen(imagenPng()));
    }

    @Test
    @DisplayName("NO elimina recursos que no pertenecen a la carpeta del sistema")
    void noEliminaRecursosAjenos() throws IOException {
        assertFalse(storageService.eliminarImagen("alguien/otro/recurso.jpg"));
        assertFalse(storageService.esRecursoPropio("carpeta_externa/foto.jpg"));
        assertFalse(storageService.esRecursoPropio(null));

        verify(uploader, org.mockito.Mockito.never()).destroy(any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("Elimina recursos propios de la carpeta nexdine/productos")
    void eliminaRecursosPropios() throws IOException {
        assertTrue(storageService.esRecursoPropio("nexdine/productos/producto_abc"));

        when(uploader.destroy(any(String.class), any(Map.class))).thenReturn(new HashMap<>());

        assertTrue(storageService.eliminarImagen("nexdine/productos/producto_abc"));
        verify(uploader).destroy(any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("Un fallo al eliminar no propaga la excepción")
    void falloAlEliminarNoPropaga() throws IOException {
        when(uploader.destroy(any(String.class), any(Map.class)))
                .thenThrow(new IOException("timeout"));

        assertFalse(storageService.eliminarImagen("nexdine/productos/producto_abc"));
    }

    @Test
    @DisplayName("Sin credenciales: estaDisponible() es false")
    void sinCredencialesNoDisponible() {
        CloudinaryProperties vacias = new CloudinaryProperties();
        ReflectionTestUtils.setField(vacias, "cloudName", "");
        ReflectionTestUtils.setField(vacias, "apiKey", "");
        ReflectionTestUtils.setField(vacias, "apiSecret", "");

        @SuppressWarnings("unchecked")
        ObjectProvider<Cloudinary> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        CloudinaryStorageService sinCredenciales = new CloudinaryStorageService(vacias, provider);

        assertFalse(sinCredenciales.estaDisponible());
        assertEquals(3, sinCredenciales.variablesFaltantes().size());
    }
}
