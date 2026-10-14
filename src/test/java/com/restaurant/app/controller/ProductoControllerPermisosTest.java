package com.restaurant.app.controller;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.CategoriaService;
import com.restaurant.app.services.CloudinaryStorageService;
import com.restaurant.app.services.ProductoImagenService;
import com.restaurant.app.services.ProductoService;
import com.restaurant.app.services.SkuService;
import com.restaurant.app.services.UsuarioContextService;
import com.restaurant.app.model.Administrador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductoController - permisos y códigos HTTP")
class ProductoControllerPermisosTest {

    @NonNull
    private static final UUID PRODUCTO_ID = TestIds.PRODUCTO_A;

    private ProductoController controller;
    private ProductoService productoService;
    private ProductoImagenService productoImagenService;
    private UsuarioContextService usuarioContextService;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        productoService = mock(ProductoService.class);
        productoImagenService = mock(ProductoImagenService.class);
        usuarioContextService = mock(UsuarioContextService.class);

        controller = new ProductoController(
                productoService,
                productoImagenService,
                mock(SkuService.class),
                mock(ProductoRepository.class),
                usuarioContextService,
                mock(SucursalRepository.class),
                mock(CategoriaService.class));

        session = new MockHttpSession();
    }

    private static Map<?, ?> cuerpo(ResponseEntity<?> respuesta) {
        Object body = respuesta.getBody();
        if (body instanceof Map<?, ?> mapa) {
            return mapa;
        }
        throw new AssertionError("El cuerpo de la respuesta no es un objeto JSON: " + body);
    }

    private Usuario usuario(String rol) {
        Usuario u = new Administrador();
        u.setId(TestIds.USUARIO_ADMIN_A);
        u.setNombre("Test");
        u.setRol(rol);
        return u;
    }

    private MockMultipartFile imagenPng() {
        byte[] png = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        };
        return new MockMultipartFile("imagen", "foto.png", "image/png", png);
    }


    @Test
    @DisplayName("Sin sesión: 401 al crear con imagen")
    void sinSesionRetorna401() {
        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
        verify(productoImagenService, never()).crearConImagen(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ADMINISTRADOR no puede crear productos con imagen: 403")
    void administradorNoPuedeCrear() {
        session.setAttribute("currentUser", usuario("ADMINISTRADOR"));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        verify(productoImagenService, never()).crearConImagen(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SUPERADMIN sí puede crear productos con imagen")
    void superadminPuedeCrear() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        Producto creado = new Producto();
        creado.setId(TestIds.PRODUCTO_A);
        creado.setImagen("https://res.cloudinary.com/demo/image/upload/nexdine/productos/a.jpg");
        creado.setImagenPublicId("nexdine/productos/a");

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.OK, r.getStatusCode());

        Object body = r.getBody();
        assertTrue(body instanceof Producto);
        assertEquals("https://res.cloudinary.com/demo/image/upload/nexdine/productos/a.jpg",
                ((Producto) body).getImagen());
    }

    @Test
    @DisplayName("SUPERADMIN no puede editar producto de otro restaurante: 403")
    void superadminNoPuedeEditarProductoAjeno() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        Producto ajeno = new Producto();
        ajeno.setId(TestIds.PRODUCTO_B);
        ajeno.setRestauranteId(TestIds.RESTAURANTE_B);
        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(ajeno);

        ResponseEntity<?> r = controller.actualizarProductoConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        verify(productoImagenService, never()).actualizarConImagen(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin sesión: 401 al editar con imagen")
    void sinSesionNoPuedeEditar() {
        ResponseEntity<?> r = controller.actualizarProductoConImagen(
                PRODUCTO_ID, "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }


    @Test
    @DisplayName("Nombre vacío: 400 sin tocar el servicio")
    void nombreVacioRetorna400() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "   ", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        verify(productoImagenService, never()).crearConImagen(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Precio 0: 400")
    void precioCeroRetorna400() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, BigDecimal.ZERO,
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    @DisplayName("Sin sucursales: 400")
    void sinSucursalesRetorna400() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(), session);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }


    @Test
    @DisplayName("Archivo demasiado grande: 413")
    void archivoGrandeRetorna413() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.restaurant.app.services.ImagenProductoValidador
                        .ImagenDemasiadoGrandeException(6L * 1024 * 1024, 5L * 1024 * 1024));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, r.getStatusCode());
    }

    @Test
    @DisplayName("Archivo inválido: 400")
    void archivoInvalidoRetorna400() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.restaurant.app.services.ImagenProductoValidador
                        .ImagenInvalidaException("El archivo enviado no es una imagen valida."));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
    }

    @Test
    @DisplayName("Falta de credenciales: 503 con los NOMBRES de las variables, sin secretos")
    void credencialesAusentesRetorna503() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CloudinaryStorageService.CloudinaryNoConfiguradoException(
                        List.of("CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET")));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());

        Map<?, ?> body = cuerpo(r);
        assertNotNull(body.get("variablesFaltantes"));
        assertTrue(String.valueOf(body.get("variablesFaltantes")).contains("CLOUDINARY_CLOUD_NAME"));

        String respuesta = String.valueOf(body);
        assertTrue(!respuesta.contains("CLOUDINARY_API_SECRET=x"));
        assertTrue(!respuesta.contains("apiSecret\":\""));
        assertEquals(3, ((List<?>) body.get("variablesFaltantes")).size());
    }

    @Test
    @DisplayName("Fallo de comunicación con Cloudinary: 502")
    void falloDeComunicacionRetorna502() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CloudinaryStorageService.CloudinaryCommunicationException(
                        "No fue posible subir la imagen a Cloudinary."));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.BAD_GATEWAY, r.getStatusCode());
    }

    @Test
    @DisplayName("Producto inexistente al editar: 404")
    void productoInexistenteRetorna404() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));
        when(productoService.getProductoById(TestIds.PRODUCTO_B)).thenReturn(null);

        ResponseEntity<?> r = controller.actualizarProductoConImagen(
                TestIds.PRODUCTO_B, "Hamburguesa", null, new BigDecimal("28900"),
                imagenPng(), null, null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
    }


    @Test
    @DisplayName("GET /productos/imagenes/config no expone secretos")
    void configuracionNoExponeSecretos() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(productoImagenService.cloudinaryDisponible()).thenReturn(true);
        when(productoImagenService.getMaxBytes()).thenReturn(5L * 1024 * 1024);

        ResponseEntity<?> r = controller.configuracionImagenes(session);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        Map<?, ?> body = cuerpo(r);
        assertEquals(true, body.get("cloudinaryDisponible"));
        assertEquals(5L * 1024 * 1024, body.get("tamanoMaximoBytes"));
        assertEquals(List.of("JPEG", "PNG", "WebP"), body.get("formatos"));
    }

    @Test
    @DisplayName("GET /productos/imagenes/config sin sesión: 401")
    void configuracionSinSesion401() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.configuracionImagenes(session).getStatusCode());
    }

    @Test
    @DisplayName("GET /productos/imagenes/config con rol no autorizado: 403")
    void configuracionRolNoAutorizado403() {
        session.setAttribute("currentUser", usuario("CLIENTE"));
        assertEquals(HttpStatus.FORBIDDEN, controller.configuracionImagenes(session).getStatusCode());
    }
}
