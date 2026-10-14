package com.restaurant.app.controller;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.model.Administrador;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.CategoriaService;
import com.restaurant.app.services.ProductoImagenService;
import com.restaurant.app.services.ProductoService;
import com.restaurant.app.services.SkuService;
import com.restaurant.app.services.UsuarioContextService;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockHttpSession;

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

@DisplayName("ProductoController - 409 por duplicado y contratos SKU")
class ProductoControllerSkuTest {

    private ProductoController controller;
    private ProductoService productoService;
    private ProductoImagenService productoImagenService;
    private SkuService skuService;
    private ProductoRepository productoRepository;
    private UsuarioContextService usuarioContextService;

    private MockHttpSession session;

    @NonNull
    private static final UUID PRODUCTO_ID = TestIds.PRODUCTO_A;

    @BeforeEach
    void setUp() {
        productoService = mock(ProductoService.class);
        productoImagenService = mock(ProductoImagenService.class);
        skuService = mock(SkuService.class);
        productoRepository = mock(ProductoRepository.class);
        usuarioContextService = mock(UsuarioContextService.class);

        controller = new ProductoController(
                productoService,
                productoImagenService,
                skuService,
                productoRepository,
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

    private void comoSuperadmin() {
        session.setAttribute("currentUser", usuario("SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(any()))
                .thenReturn(new UsuarioContextService.UsuarioContext(TestIds.RESTAURANTE_A, null, "SUPERADMIN"));
    }


    @Test
    @DisplayName("Crear duplicado responde 409 Conflict")
    void crearDuplicadoRetorna409() {
        comoSuperadmin();

        when(productoService.createProducto(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductoService.ProductoDuplicadoException(
                        "Hamburguesa Clásica"));

        ResponseEntity<?> r = controller.agregarProducto(
                "Hamburguesa Clásica", null, new BigDecimal("18000"),
                null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());

        Map<?, ?> body = cuerpo(r);
        assertNotNull(body.get("error"));
        assertTrue(String.valueOf(body.get("error")).contains("Ya existe un producto"));
    }

    @Test
    @DisplayName("Editar duplicado responde 409 Conflict")
    void editarDuplicadoRetorna409() {
        comoSuperadmin();

        Producto actual = new Producto();
        actual.setId(TestIds.PRODUCTO_A);
        actual.setRestauranteId(TestIds.RESTAURANTE_A);
        when(productoService.getProductoById(PRODUCTO_ID)).thenReturn(actual);

        when(productoService.updateProducto(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductoService.ProductoDuplicadoException("Pizza Margarita"));

        ResponseEntity<?> r = controller.actualizarProducto(
                PRODUCTO_ID, "Pizza Margarita", null, new BigDecimal("18000"),
                null, null, null, session);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }

    @Test
    @DisplayName("409 en la variante multipart también")
    void multipartDuplicadoRetorna409() {
        comoSuperadmin();

        when(productoImagenService.crearConImagen(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ProductoService.ProductoDuplicadoException("Hamburguesa"));

        ResponseEntity<?> r = controller.agregarProductoConImagen(
                "Hamburguesa", null, new BigDecimal("18000"),
                null, null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }


    @Test
    @DisplayName("ADMINISTRADOR no puede crear (sin cambios en roles)")
    void administradorNoPuedeCrear() {
        session.setAttribute("currentUser", usuario("ADMINISTRADOR"));

        ResponseEntity<?> r = controller.agregarProducto(
                "Hamburguesa", null, new BigDecimal("18000"),
                null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        verify(productoService, never()).createProducto(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin sesión sigue respondiendo 401")
    void sinSesion401() {
        assertEquals(HttpStatus.UNAUTHORIZED, controller.agregarProducto(
                "X", null, new BigDecimal("1"), null, List.of(TestIds.SUCURSAL_A1), session).getStatusCode());
    }

    @Test
    @DisplayName("No se expone el SKU como parámetro de entrada: el frontend no lo elige")
    void frontendNoEligeSku() throws Exception {
        comoSuperadmin();

        Producto creado = new Producto();
        creado.setId(TestIds.PRODUCTO_A);
        creado.setSku("EB-R001-000001");
        when(productoService.createProducto(any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        ResponseEntity<?> r = controller.agregarProducto(
                "Hamburguesa", null, new BigDecimal("18000"), null, List.of(TestIds.SUCURSAL_A1), session);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        var metodo = ProductoController.class.getMethod("agregarProducto",
                String.class, String.class, BigDecimal.class, String.class, List.class, HttpSession.class);
        assertEquals(6, metodo.getParameterCount());
    }


    @Test
    @DisplayName("GET /productos/sku/estado informa sin datos sensibles")
    void estadoSku() {
        comoSuperadmin();

        when(skuService.consultarEstado())
                .thenReturn(new SkuService.EstadoSku(0L, 0L));
        when(productoRepository.countBySkuIsNull()).thenReturn(0L);

        ResponseEntity<?> r = controller.estadoSku(session);

        assertEquals(HttpStatus.OK, r.getStatusCode());

        Map<?, ?> body = cuerpo(r);
        assertEquals(0L, body.get("restaurantesSinCodigo"));
        assertEquals(true, body.get("migracionCompleta"));
        assertEquals(0L, body.get("productosSinSku"));
        assertNotNull(body.get("formato"));
    }

    @Test
    @DisplayName("El estado del SKU requiere SUPERADMIN")
    void estadoSkuSoloSuperadmin() {
        session.setAttribute("currentUser", usuario("ADMINISTRADOR"));
        assertEquals(HttpStatus.FORBIDDEN, controller.estadoSku(session).getStatusCode());

        MockHttpSession sinSesion = new MockHttpSession();
        assertEquals(HttpStatus.UNAUTHORIZED, controller.estadoSku(sinSesion).getStatusCode());
    }

    @Test
    @DisplayName("El estado del SKU reporta migración pendiente")
    void estadoSkuMigracionPendiente() {
        comoSuperadmin();

        when(skuService.consultarEstado())
                .thenReturn(new SkuService.EstadoSku(2L, 1L));
        when(productoRepository.countBySkuIsNull()).thenReturn(5L);

        Map<?, ?> body = cuerpo(controller.estadoSku(session));

        assertEquals(2L, body.get("restaurantesSinCodigo"));
        assertEquals(5L, body.get("productosSinSku"));
        assertEquals(false, body.get("migracionCompleta"));
    }
}
