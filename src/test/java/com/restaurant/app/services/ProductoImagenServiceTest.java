package com.restaurant.app.services;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.config.CloudinaryProperties;
import com.restaurant.app.model.Producto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductoImagenService - consistencia Cloudinary/PostgreSQL")
class ProductoImagenServiceTest {

    private ProductoService productoService;
    private CloudinaryStorageService cloudinaryStorageService;
    private @NonNull ImagenProductoValidador validador = new ImagenProductoValidador();
    private ProductoImagenService productoImagenService;

    private static final UUID RESTAURANTE = TestIds.RESTAURANTE_A;
    @NonNull
    private static final UUID PRODUCTO_ID = TestIds.PRODUCTO_A;

    @BeforeEach
    void setUp() {
        productoService = mock(ProductoService.class);
        cloudinaryStorageService = mock(CloudinaryStorageService.class);
        validador = new ImagenProductoValidador();
        ReflectionTestUtils.setField(validador, "maxBytes", 5L * 1024 * 1024);

        productoImagenService = new ProductoImagenService(
                productoService, cloudinaryStorageService, validador);
    }

    private Producto producto(String imagen, String publicId) {
        Producto p = new Producto();
        p.setId(PRODUCTO_ID);
        p.setNombre("Hamburguesa");
        p.setPrecio(new BigDecimal("28900"));
        p.setRestauranteId(RESTAURANTE);
        p.setImagen(imagen);
        p.setImagenPublicId(publicId);
        p.setDisponible(true);
        return p;
    }

    private final java.util.concurrent.atomic.AtomicReference<Producto> base =
            new java.util.concurrent.atomic.AtomicReference<>();

    private void simularBdConProducto(Producto inicial) {
        base.set(inicial);

        when(productoService.getProductoById(PRODUCTO_ID))
                .thenAnswer(inv -> base.get());

        when(productoService.updateProducto(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Producto p = base.get();
                    if (p == null) {
                        p = new Producto();
                        p.setId(PRODUCTO_ID);
                        base.set(p);
                    }
                    p.setNombre(inv.getArgument(1));
                    p.setPrecio(inv.getArgument(3));

                    if (inv.getArgument(4) != null) {
                        p.setImagen((String) inv.getArgument(4));
                        p.setImagenPublicId((String) inv.getArgument(5));
                    }
                    return p;
                });

        doAnswer(inv -> {
            Producto p = base.get();
            if (p != null) {
                p.setImagen(inv.getArgument(1));
                p.setImagenPublicId(inv.getArgument(2));
            }
            return null;
        }).when(productoService).asignarImagen(any(), any(), any());
    }

    private MockMultipartFile archivoPngValido() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        return new MockMultipartFile("imagen", "foto.png", "image/png", png);
    }


    @Test
    @DisplayName("CREAR con archivo: sube a Cloudinary y persiste la URL HTTPS")
    void crearConArchivo() {
        String url = "https://res.cloudinary.com/demo/image/upload/nexdine/productos/abc123.jpg";
        CloudinaryStorageService.ResultadoSubida subida =
                new CloudinaryStorageService.ResultadoSubida(url, "nexdine/productos/abc123");

        when(cloudinaryStorageService.subirImagen(any())).thenReturn(subida);
        when(productoService.createProducto(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Producto p = new Producto();
                    p.setId(PRODUCTO_ID);
                    p.setRestauranteId(RESTAURANTE);
                    p.setPrecio(new BigDecimal("28900"));
                    p.setImagen(inv.getArgument(3));
                    return p;
                });

        Producto creado = productoImagenService.crearConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                archivoPngValido(), null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals(url, creado.getImagen());
        assertEquals("nexdine/productos/abc123", creado.getImagenPublicId());
        verify(productoService).asignarImagen(PRODUCTO_ID, url, "nexdine/productos/abc123");
    }

    @Test
    @DisplayName("CREAR con archivo: si la BD falla, se elimina el recurso subido (compensación)")
    void crearCompensaSiFallaLaBaseDeDatos() {
        CloudinaryStorageService.ResultadoSubida subida =
                new CloudinaryStorageService.ResultadoSubida(
                        "https://res.cloudinary.com/x/nexdine/productos/abc123.jpg",
                        "nexdine/productos/abc123");

        when(cloudinaryStorageService.subirImagen(any())).thenReturn(subida);
        when(cloudinaryStorageService.esRecursoPropio("nexdine/productos/abc123")).thenReturn(true);
        when(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc123", null))
                .thenReturn(true);
        when(productoService.createProducto(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("La sucursal seleccionada no existe."));

        assertThrows(IllegalArgumentException.class, () -> productoImagenService.crearConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                archivoPngValido(), null, RESTAURANTE, List.of(TestIds.SUCURSAL_B1)));

        verify(cloudinaryStorageService).eliminarImagen("nexdine/productos/abc123");
    }

    @Test
    @DisplayName("CREAR sin archivo: no se toca Cloudinary (compatibilidad actual)")
    void crearSinArchivoNoTocaCloudinary() {
        when(productoService.createProducto(any(), any(), any(), any(), any(), any()))
                .thenReturn(producto("https://ejemplo.com/foto.jpg", null));

        Producto creado = productoImagenService.crearConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                null, "https://ejemplo.com/foto.jpg", RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals("https://ejemplo.com/foto.jpg", creado.getImagen());
        verify(cloudinaryStorageService, never()).subirImagen(any());
    }


    @Test
    @DisplayName("EDITAR sin archivo: conserva la imagen actual y NO toca Cloudinary")
    void editarSinArchivoConservaImagen() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");

        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);
        when(productoService.updateProducto(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> actual);

        Producto resultado = productoImagenService.actualizarConImagen(
                PRODUCTO_ID, "Hamburguesa", "Nueva descripción", new BigDecimal("28900"),
                null, null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals("https://res.cloudinary.com/x/nexdine/productos/abc.jpg", resultado.getImagen());
        assertEquals("nexdine/productos/abc", resultado.getImagenPublicId());

        verify(cloudinaryStorageService, never()).subirImagen(any());
        verify(cloudinaryStorageService, never()).eliminarImagen(anyString());
        verify(productoService, never()).asignarImagen(any(), any(), any());
    }

    @Test
    @DisplayName("EDITAR con imagen nueva: actualiza el MISMO producto y libera la anterior al final")
    void editarConImagenNueva() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");
        simularBdConProducto(actual);

        String nuevaUrl = "https://res.cloudinary.com/x/nexdine/productos/nuevo.jpg";
        CloudinaryStorageService.ResultadoSubida subida =
                new CloudinaryStorageService.ResultadoSubida(nuevaUrl, "nexdine/productos/nuevo");

        when(cloudinaryStorageService.subirImagen(any())).thenReturn(subida);
        when(cloudinaryStorageService.esRecursoPropio("nexdine/productos/abc")).thenReturn(true);
        when(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(true);

        Producto resultado = productoImagenService.actualizarConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                archivoPngValido(), null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals(nuevaUrl, resultado.getImagen());
        assertEquals("nexdine/productos/nuevo", resultado.getImagenPublicId());

        verify(productoService, never()).createProducto(any(), any(), any(), any(), any(), any());

        verify(productoService).asignarImagen(PRODUCTO_ID, nuevaUrl, "nexdine/productos/nuevo");
        verify(cloudinaryStorageService).eliminarImagen("nexdine/productos/abc");
    }

    @Test
    @DisplayName("EDITAR con imagen nueva: si la subida falla, el producto NO se toca")
    void editarSiFallaLaSubidaNoTocaLaBaseDeDatos() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");

        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);
        when(cloudinaryStorageService.subirImagen(any()))
                .thenThrow(new CloudinaryStorageService.CloudinaryCommunicationException(
                        "No fue posible subir la imagen a Cloudinary."));

        assertThrows(CloudinaryStorageService.CloudinaryCommunicationException.class,
                () -> productoImagenService.actualizarConImagen(
                        PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                        archivoPngValido(), null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1)));

        verify(productoService, never()).updateProducto(any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(cloudinaryStorageService, never()).eliminarImagen(anyString());
    }

    @Test
    @DisplayName("EDITAR con archivo inválido: se rechaza ANTES de subir nada")
    void editarConArchivoInvalidoNoSube() {
        Producto actual = producto("https://x/abc.jpg", "nexdine/productos/abc");
        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);

        MockMultipartFile pdf = new MockMultipartFile(
                "imagen", "malicioso.png", "image/png", "%PDF-1.7".getBytes());

        assertThrows(ImagenProductoValidador.ImagenInvalidaException.class,
                () -> productoImagenService.actualizarConImagen(
                        PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                        pdf, null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1)));

        verify(cloudinaryStorageService, never()).subirImagen(any());
        verify(productoService, never()).updateProducto(any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("EDITAR con archivo demasiado grande: se rechaza ANTES de subir nada")
    void editarConArchivoGrandeNoSube() {
        Producto actual = producto("https://x/abc.jpg", "nexdine/productos/abc");
        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);

        byte[] grande = new byte[(5 * 1024 * 1024) + 1];
        MockMultipartFile archivo = new MockMultipartFile(
                "imagen", "grande.png", "image/png", grande);

        assertThrows(ImagenProductoValidador.ImagenDemasiadoGrandeException.class,
                () -> productoImagenService.actualizarConImagen(
                        PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                        archivo, null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1)));

        verify(cloudinaryStorageService, never()).subirImagen(any());
    }

    @Test
    @DisplayName("EDITAR no borra un recurso que otro producto sigue usando")
    void editarNoBorraRecursoCompartido() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");
        simularBdConProducto(actual);

        CloudinaryStorageService.ResultadoSubida subida =
                new CloudinaryStorageService.ResultadoSubida(
                        "https://res.cloudinary.com/x/nexdine/productos/nuevo.jpg",
                        "nexdine/productos/nuevo");

        when(cloudinaryStorageService.subirImagen(any())).thenReturn(subida);
        when(cloudinaryStorageService.esRecursoPropio("nexdine/productos/abc")).thenReturn(true);
        when(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(false);

        productoImagenService.actualizarConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                archivoPngValido(), null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        verify(cloudinaryStorageService, never()).eliminarImagen(anyString());
    }

    @Test
    @DisplayName("EDITAR no borra recursos externos (URLs de terceros)")
    void editarNoBorraRecursosExternos() {
        Producto actual = producto("https://miservidor.com/foto.jpg", null);
        simularBdConProducto(actual);

        CloudinaryStorageService.ResultadoSubida subida =
                new CloudinaryStorageService.ResultadoSubida(
                        "https://res.cloudinary.com/x/nexdine/productos/nuevo.jpg",
                        "nexdine/productos/nuevo");

        when(cloudinaryStorageService.subirImagen(any())).thenReturn(subida);

        productoImagenService.actualizarConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                archivoPngValido(), null, null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        verify(cloudinaryStorageService, never()).eliminarImagen(anyString());
    }

    @Test
    @DisplayName("EDITAR con URL de texto: libera el recurso de Cloudinary anterior")
    void editarConUrlDeTexto() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");
        simularBdConProducto(actual);

        when(cloudinaryStorageService.esRecursoPropio("nexdine/productos/abc")).thenReturn(true);
        when(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(true);

        Producto resultado = productoImagenService.actualizarConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                null, "https://miservidor.com/nueva.jpg", null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals("https://miservidor.com/nueva.jpg", resultado.getImagen());
        assertNull(resultado.getImagenPublicId());
        verify(productoService).asignarImagen(PRODUCTO_ID, "https://miservidor.com/nueva.jpg", null);
        verify(cloudinaryStorageService).eliminarImagen("nexdine/productos/abc");
    }


    @Test
    @DisplayName("ELIMINAR producto: borra la BD primero y luego limpia Cloudinary")
    void eliminarProductoLimpiaImagen() {
        Producto actual = producto("https://res.cloudinary.com/x/nexdine/productos/abc.jpg",
                "nexdine/productos/abc");

        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);
        when(cloudinaryStorageService.esRecursoPropio("nexdine/productos/abc")).thenReturn(true);
        when(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(true);

        productoImagenService.eliminarProductoYLimpiarImagen(PRODUCTO_ID);

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(productoService, cloudinaryStorageService);
        orden.verify(productoService).eliminarProducto(PRODUCTO_ID);
        orden.verify(cloudinaryStorageService).eliminarImagen("nexdine/productos/abc");
    }

    @Test
    @DisplayName("ELIMINAR producto antiguo sin imagen: no intenta borrar nada")
    void eliminarProductoSinImagen() {
        Producto actual = producto(null, null);
        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);

        productoImagenService.eliminarProductoYLimpiarImagen(PRODUCTO_ID);

        verify(productoService).eliminarProducto(PRODUCTO_ID);
        verify(cloudinaryStorageService, never()).eliminarImagen(anyString());
    }


    @Test
    @DisplayName("Sin credenciales: error controlado con el NOMBRE de la variable faltante")
    void errorCuandoFaltanCredenciales() {
        CloudinaryProperties properties = new CloudinaryProperties();
        ReflectionTestUtils.setField(properties, "cloudName", "");
        ReflectionTestUtils.setField(properties, "apiKey", "");
        ReflectionTestUtils.setField(properties, "apiSecret", "");

        @SuppressWarnings("unchecked")
        ObjectProvider<com.cloudinary.Cloudinary> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        CloudinaryStorageService storage = new CloudinaryStorageService(properties, provider);

        CloudinaryStorageService.CloudinaryNoConfiguradoException ex = assertThrows(
                CloudinaryStorageService.CloudinaryNoConfiguradoException.class,
                () -> storage.subirImagen(archivoPngValido()));

        assertTrue(ex.getMessage().contains("CLOUDINARY_CLOUD_NAME"));
        assertTrue(ex.getMessage().contains("CLOUDINARY_API_KEY"));
        assertTrue(ex.getMessage().contains("CLOUDINARY_API_SECRET"));

        assertTrue(!ex.getMessage().contains("secret-value"));
    }

    @Test
    @DisplayName("CloudinaryStorageService: toString de properties no expone el API Secret")
    void propertiesNoExponeElSecreto() {
        CloudinaryProperties properties = new CloudinaryProperties();
        ReflectionTestUtils.setField(properties, "cloudName", "demo");
        ReflectionTestUtils.setField(properties, "apiKey", "1234567890");
        ReflectionTestUtils.setField(properties, "apiSecret", "SECRET_VALUE_123");

        String representacion = properties.toString();

        assertTrue(!representacion.contains("SECRET_VALUE_123"));
        assertTrue(representacion.contains("presente"));
    }
}
