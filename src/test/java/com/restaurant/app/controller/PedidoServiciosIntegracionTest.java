package com.restaurant.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.app.dto.PedidoManualRequest;
import com.restaurant.app.model.EmpleadoSucursal;
import com.restaurant.app.model.HistorialPedido;
import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Mesero;
import com.restaurant.app.model.Notificacion;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.HistorialPedidoRepository;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.NotificacionRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.repository.UserRepository;
import com.restaurant.app.services.PagoService;
import com.restaurant.app.services.PedidoService;
import com.restaurant.app.util.CatalogoPedidos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PedidoServiciosIntegracionTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private RestauranteRepository restaurantes;
    @Autowired
    private SucursalRepository sucursales;
    @Autowired
    private ProductoRepository productos;
    @Autowired
    private ProductoSucursalRepository productoSucursales;
    @Autowired
    private PedidoRepository pedidos;
    @Autowired
    private ItemPedidoRepository items;
    @Autowired
    private MesaRepository mesas;
    @Autowired
    private EmpleadoSucursalRepository empleadoSucursales;
    @Autowired
    private UserRepository usuarios;
    @Autowired
    private NotificacionRepository notificaciones;
    @Autowired
    private HistorialPedidoRepository historial;
    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private PagoService pagoService;
    @Autowired
    private ObjectMapper mapper;

    private Restaurante restaurante;
    private Sucursal sucursal;
    private Producto producto;
    private Mesero mesero;
    private Mesa mesa;

    @BeforeEach
    void setUp() {
        restaurante = new Restaurante();
        restaurante.setNombre("Restaurante Servicios " + UUID.randomUUID());
        restaurante.setActivo(true);
        restaurantes.save(restaurante);

        sucursal = new Sucursal();
        sucursal.setRestauranteId(restaurante.getId());
        sucursal.setNombre("Sucursal Servicios " + UUID.randomUUID());
        sucursal.setActiva(true);
        sucursales.save(sucursal);

        producto = new Producto();
        producto.setNombre("Producto Servicios " + UUID.randomUUID());
        producto.setPrecio(new BigDecimal("25.00"));
        producto.setRestauranteId(restaurante.getId());
        producto.setDisponible(true);
        productos.save(producto);

        productoSucursales.save(new ProductoSucursal(
                producto.getId(), sucursal.getId(), restaurante.getId()));

        mesero = new Mesero();
        mesero.setNombre("Mesero Servicios " + UUID.randomUUID());
        mesero.setRol("MESERO");
        mesero.setEstado(true);
        usuarios.save(mesero);
        empleadoSucursales.save(new EmpleadoSucursal(mesero.getId(), sucursal.getId()));

        mesa = new Mesa();
        mesa.setSucursalId(sucursal.getId());
        mesa.setNombre("Mesa Servicios");
        mesa.setCodigo("mesa-" + UUID.randomUUID().toString().substring(0, 8));
        mesa.setActiva(true);
        mesa.setEstado(CatalogoPedidos.MESA_LIBRE);
        mesas.save(mesa);
    }

    @AfterEach
    void limpiar() {
        notificaciones.deleteAll();
        historial.deleteAll();
        items.deleteAll();
        pedidos.deleteAll();
        mesas.deleteAll();
        empleadoSucursales.deleteAll();
        if (mesero != null) {
            usuarios.deleteById(mesero.getId());
        }
        productoSucursales.deleteAll();
        productos.deleteAll();
        sucursales.deleteAll();
        restaurantes.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void registraParaLlevarCalculaTotalYPagoPendiente() {
        Map<String, Object> cuerpo = Map.of(
                "nombre", "Cliente ParaLlevar",
                "telefono", "3001234567",
                "sucursalId", sucursal.getId().toString(),
                "items", List.of(Map.of(
                        "productoId", producto.getId().toString(),
                        "cantidad", 2)));

        ResponseEntity<String> respuesta = rest.postForEntity(
                "/api/para-llevar/pedidos", json(cuerpo), String.class);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        Map<String, Object> resumen = leerJson(respuesta.getBody());
        assertEquals("PARA_LLEVAR", resumen.get("tipoServicio"));
        assertEquals("PENDIENTE", resumen.get("pagoEstado"));
        assertEquals(0, new BigDecimal("50.00").compareTo(
                new BigDecimal(String.valueOf(resumen.get("total")))));
        assertEquals(1, ((List<Object>) resumen.get("items")).size());
    }

    @Test
    void registroDomicilioExigeDireccionYaplicaContraEntregaConEfectivo() {
        Map<String, Object> sinDireccion = Map.of(
                "nombre", "Cliente Domicilio",
                "telefono", "3001234567",
                "sucursalId", sucursal.getId().toString(),
                "items", List.of(Map.of(
                        "productoId", producto.getId().toString(),
                        "cantidad", 1)));
        assertEquals(HttpStatus.BAD_REQUEST, rest.postForEntity(
                "/api/domicilios/pedidos", json(sinDireccion), String.class)
                .getStatusCode());

        Map<String, Object> conDireccion = Map.of(
                "nombre", "Cliente Domicilio",
                "telefono", "3001234567",
                "direccion", "Calle 123",
                "sucursalId", sucursal.getId().toString(),
                "items", List.of(Map.of(
                        "productoId", producto.getId().toString(),
                        "cantidad", 3)));
        ResponseEntity<String> respuesta = rest.postForEntity(
                "/api/domicilios/pedidos", json(conDireccion), String.class);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        Map<String, Object> resumen = leerJson(respuesta.getBody());
        UUID pedidoId = UUID.fromString(String.valueOf(resumen.get("id")));
        assertEquals("Calle 123", resumen.get("direccion"));

        Pedido pedido = pagoService.solicitarMetodoPago(null, pedidoId, "CONTRA_ENTREGA");
        assertEquals("CONTRA_ENTREGA", pedido.getPagoMetodo());
        assertEquals("PENDIENTE", pedido.getPagoEstado());

        pedido = pagoService.registrarMontoEfectivo(null, pedidoId, new BigDecimal("100.00"));
        assertEquals(0, new BigDecimal("25.00").compareTo(pedido.getPagoCambio()));
        assertEquals("PENDIENTE", pedido.getPagoEstado());

        Map<String, Object> pago = pagoService.obtenerPago(null, pedidoId);
        assertEquals("CONTRA_ENTREGA", pago.get("pagoMetodo"));
        assertEquals("PENDIENTE", pago.get("pagoEstado"));
        assertEquals(0, new BigDecimal("25.00").compareTo(
                new BigDecimal(String.valueOf(pago.get("pagoCambio")))));
    }

    @Test
    void transicionesDeEstadoRespetanElCicloYregistranHistorial() {
        Pedido pedido = crearPedidoManual();
        UUID pedidoId = pedido.getId();
        assertEquals("PENDIENTE", pedido.getEstado());

        pedido = pedidoService.actualizarEstado(mesero, pedidoId, "CONFIRMADO");
        assertEquals("CONFIRMADO", pedido.getEstado());

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.actualizarEstado(mesero, pedidoId, "ENTREGADO"));
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.actualizarEstado(mesero, pedidoId, "PENDIENTE"));

        pedido = pedidoService.actualizarEstado(mesero, pedidoId, "EN_PREPARACION");
        assertEquals("EN_PREPARACION", pedido.getEstado());
        pedido = pedidoService.actualizarEstado(mesero, pedidoId, "LISTO");
        assertEquals("LISTO", pedido.getEstado());
        pedido = pedidoService.actualizarEstado(mesero, pedidoId, "ENTREGADO");
        assertEquals("ENTREGADO", pedido.getEstado());

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.actualizarEstado(mesero, pedidoId, "CONFIRMADO"));

        List<HistorialPedido> cambios = historial.findByPedidoIdOrderByFechaCambioDesc(pedidoId);
        assertEquals(4, cambios.size());
        assertEquals("ENTREGADO", cambios.get(0).getEstadoNuevo());
        assertEquals("LISTO", cambios.get(0).getEstadoAnterior());
    }

    @Test
    void liberaMesaAlConfirmarPagoYnoLaLiberaConPedidosPendientes() {
        Pedido primerPedido = crearPedidoManual();
        UUID primerId = primerPedido.getId();
        avanzarHastaEntregado(primerId);
        assertEquals(CatalogoPedidos.MESA_OCUPADA,
                mesas.findById(mesa.getId()).orElseThrow().getEstado());

        Pedido segundoPedido = crearPedidoManual();
        UUID segundoId = segundoPedido.getId();
        avanzarHastaEntregado(segundoId);

        pagoService.confirmarPago(mesero, primerId);
        assertEquals(CatalogoPedidos.MESA_OCUPADA,
                mesas.findById(mesa.getId()).orElseThrow().getEstado());

        pagoService.confirmarPago(mesero, segundoId);
        assertEquals(CatalogoPedidos.MESA_LIBRE,
                mesas.findById(mesa.getId()).orElseThrow().getEstado());

        assertTrue(notificaciones.existsByPedidoIdAndTipo(
                primerId, CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO));
        assertTrue(notificaciones.existsByPedidoIdAndTipo(
                segundoId, CatalogoPedidos.NOTIFICACION_PAGO_EXITOSO));

        long cantidad = notificaciones.findBySucursalIdInOrderByFechaCreacionDesc(
                        List.of(sucursal.getId())).stream()
                .filter(n -> primerId.equals(n.getPedidoId()))
                .count();
        assertEquals(1, cantidad);

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.confirmarPago(mesero, primerId));
    }

    private void avanzarHastaEntregado(UUID pedidoId) {
        pedidoService.actualizarEstado(mesero, pedidoId, "CONFIRMADO");
        pedidoService.actualizarEstado(mesero, pedidoId, "EN_PREPARACION");
        pedidoService.actualizarEstado(mesero, pedidoId, "LISTO");
        pedidoService.actualizarEstado(mesero, pedidoId, "ENTREGADO");
    }

    private Pedido crearPedidoManual() {
        PedidoManualRequest solicitud = new PedidoManualRequest(
                mesa.getId(),
                "Cliente Mesa",
                "3001234567",
                "",
                List.of(new PedidoManualRequest.Item(producto.getId(), 1, "")));
        return pedidoService.crearPedidoManual(mesero, solicitud);
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> cuerpo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(cuerpo, cabeceras);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leerJson(String cuerpo) {
        try {
            return mapper.readValue(cuerpo, Map.class);
        } catch (Exception e) {
            throw new AssertionError("No fue posible leer la respuesta JSON: " + cuerpo, e);
        }
    }
}
