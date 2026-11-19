package com.restaurant.app.services;

import com.restaurant.app.dto.ConfiguracionImpuestoRequest;
import com.restaurant.app.dto.PedidoManualRequest;
import com.restaurant.app.dto.PedidoServicioRequest;
import com.restaurant.app.model.Empleado;
import com.restaurant.app.model.EmpleadoSucursal;
import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Mesero;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.RestauranteSuperadmin;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.ConfiguracionImpuestoRepository;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.HistorialPedidoRepository;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.NotificacionRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.RestauranteSuperadminRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.repository.UserRepository;
import com.restaurant.app.util.CatalogoPedidos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ImpuestoIntegracionTest {

    @Autowired
    private ConfiguracionImpuestoService configuracionImpuestoService;
    @Autowired
    private PedidoService pedidoService;
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
    private ConfiguracionImpuestoRepository configuraciones;
    @Autowired
    private RestauranteSuperadminRepository restauranteSuperadmins;

    private Restaurante restaurante;
    private Sucursal sucursal;
    private Producto producto;
    private Mesa mesa;
    private Mesero mesero;
    private Empleado superadmin;

    @BeforeEach
    void setUp() {
        restaurante = new Restaurante();
        restaurante.setNombre("Restaurante Impuestos " + UUID.randomUUID());
        restaurante.setActivo(true);
        restaurantes.save(restaurante);

        sucursal = new Sucursal();
        sucursal.setRestauranteId(restaurante.getId());
        sucursal.setNombre("Sucursal Impuestos " + UUID.randomUUID());
        sucursal.setActiva(true);
        sucursales.save(sucursal);

        producto = new Producto();
        producto.setNombre("Producto Impuestos " + UUID.randomUUID());
        producto.setPrecio(new BigDecimal("100.00"));
        producto.setRestauranteId(restaurante.getId());
        producto.setDisponible(true);
        productos.save(producto);
        productoSucursales.save(new ProductoSucursal(
                producto.getId(), sucursal.getId(), restaurante.getId()));

        mesero = new Mesero();
        mesero.setNombre("Mesero Impuestos " + UUID.randomUUID());
        mesero.setRol("MESERO");
        mesero.setEstado(true);
        usuarios.save(mesero);
        empleadoSucursales.save(new EmpleadoSucursal(mesero.getId(), sucursal.getId()));

        superadmin = new Empleado();
        superadmin.setNombre("Superadmin Impuestos " + UUID.randomUUID());
        superadmin.setRol("SUPERADMIN");
        superadmin.setEstado(true);
        usuarios.save(superadmin);

        RestauranteSuperadmin vinculo = new RestauranteSuperadmin();
        vinculo.setRestauranteId(restaurante.getId());
        vinculo.setUsuarioId(superadmin.getId());
        restauranteSuperadmins.save(vinculo);

        mesa = new Mesa();
        mesa.setSucursalId(sucursal.getId());
        mesa.setNombre("Mesa Impuestos");
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
        restauranteSuperadmins.deleteAll();
        configuraciones.deleteAll();
        usuarios.deleteById(mesero.getId());
        usuarios.deleteById(superadmin.getId());
        productoSucursales.deleteAll();
        productos.deleteAll();
        sucursales.deleteAll();
        restaurantes.deleteAll();
    }

    @Test
    void soloElSuperadminModificaYElPersonalConsulta() {
        assertThrows(IllegalStateException.class,
                () -> configuracionImpuestoService.actualizar(mesero, solicitudImpuesto("19", true)));

        var configuracion = configuracionImpuestoService.consultar(mesero);
        assertNull(configuracion.id());
        assertEquals(0, BigDecimal.ZERO.compareTo(configuracion.porcentaje()));
    }

    @Test
    void rechazaPorcentajesInvalidosSinPersistirConfiguracion() {
        for (String valor : List.of("-1", "100.01", "19.999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> configuracionImpuestoService.actualizar(superadmin,
                            solicitudImpuesto(valor, true)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> configuracionImpuestoService.actualizar(superadmin,
                        new ConfiguracionImpuestoRequest(null, true)));

        assertNull(configuracionImpuestoService.consultar(superadmin).id());
    }

    @Test
    void aplicaElImpuestoConfiguradoATodasLasModalidades() {
        configurar("19.00", true);

        Pedido paraLlevar = pedidoService.crearPedidoParaLlevar(
                solicitudServicio("Cliente ParaLlevar", null));
        Pedido domicilio = pedidoService.crearPedidoDomicilio(
                solicitudServicio("Cliente Domicilio", "Calle 123"));
        Pedido enMesa = pedidoService.crearPedidoManual(mesero,
                new PedidoManualRequest(mesa.getId(), "Cliente Mesa", "3001234567", null,
                        List.of(new PedidoManualRequest.Item(producto.getId(), 1, null))));

        for (Pedido pedido : List.of(paraLlevar, domicilio, enMesa)) {
            Map<String, Object> resumen = pedidoService.obtenerResumen(pedido.getId());
            assertEquals(0, new BigDecimal("100.00").compareTo(
                    (BigDecimal) resumen.get("baseGravable")));
            assertEquals(0, new BigDecimal("19.00").compareTo(
                    (BigDecimal) resumen.get("impuestos")));
            assertEquals(0, new BigDecimal("119.00").compareTo(
                    (BigDecimal) resumen.get("total")));
            assertEquals(0, new BigDecimal("19.00").compareTo(
                    (BigDecimal) resumen.get("tasaImpuesto")));
        }
    }

    @Test
    void unPedidoPendienteConservaLaTasaConLaQueFueCreado() {
        configurar("19.00", true);
        Pedido pedido = pedidoService.crearPedidoParaLlevar(
                solicitudServicio("Cliente Snapshot", null));

        configurar("10.00", true);
        pedidoService.agregarItem(pedido.getId(), producto.getId(), 1, null, null);

        Map<String, Object> resumen = pedidoService.obtenerResumen(pedido.getId());
        assertEquals(0, new BigDecimal("19.00").compareTo(
                (BigDecimal) resumen.get("tasaImpuesto")));
        assertEquals(0, new BigDecimal("38.00").compareTo(
                (BigDecimal) resumen.get("impuestos")));
        assertEquals(0, new BigDecimal("238.00").compareTo(
                (BigDecimal) resumen.get("total")));
    }

    @Test
    void unPedidoConfirmadoNoSeRecalculaAlCambiarElImpuesto() {
        configurar("19.00", true);
        Pedido pedido = pedidoService.crearPedidoParaLlevar(
                solicitudServicio("Cliente Confirmado", null));
        pedidoService.confirmarPedido(pedido.getId(), null, null, null, null);

        configurar("5.00", true);

        Map<String, Object> resumen = pedidoService.obtenerResumen(pedido.getId());
        assertEquals("CONFIRMADO", resumen.get("estado"));
        assertEquals(0, new BigDecimal("19.00").compareTo(
                (BigDecimal) resumen.get("tasaImpuesto")));
        assertEquals(0, new BigDecimal("19.00").compareTo(
                (BigDecimal) resumen.get("impuestos")));
        assertEquals(0, new BigDecimal("119.00").compareTo(
                (BigDecimal) resumen.get("total")));
    }

    @Test
    void unPedidoInvitadoConservaLaTasaDeSuCreacion() {
        configurar("19.00", true);
        Pedido pedido = pedidoService.crearPedidoInvitado(
                "Cliente Invitado", "3001234567", "PARA_LLEVAR",
                null, null, sucursal.getId());

        configurar("10.00", true);
        pedidoService.agregarItem(pedido.getId(), producto.getId(), 1, null, null);

        Map<String, Object> resumen = pedidoService.obtenerResumen(pedido.getId());
        assertEquals(0, new BigDecimal("19.00").compareTo(
                (BigDecimal) resumen.get("tasaImpuesto")));
        assertEquals(0, new BigDecimal("119.00").compareTo(
                (BigDecimal) resumen.get("total")));
    }

    @Test
    void impuestoInactivoAplicaCeroANuevosPedidos() {
        configurar("19.00", true);
        configuracionImpuestoService.cambiarEstado(superadmin, false);

        Pedido pedido = pedidoService.crearPedidoParaLlevar(
                solicitudServicio("Cliente Sin Impuesto", null));

        Map<String, Object> resumen = pedidoService.obtenerResumen(pedido.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                (BigDecimal) resumen.get("impuestos")));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                (BigDecimal) resumen.get("total")));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                configuracionImpuestoService.obtenerTasaVigente(restaurante.getId())));
        var configuracion = configuracionImpuestoService.consultar(superadmin);
        assertEquals(0, new BigDecimal("19.00").compareTo(configuracion.porcentaje()));
        assertFalse(configuracion.activo());
    }

    private void configurar(String porcentaje, boolean activo) {
        configuracionImpuestoService.actualizar(superadmin,
                solicitudImpuesto(porcentaje, activo));
    }

    private ConfiguracionImpuestoRequest solicitudImpuesto(String porcentaje, boolean activo) {
        return new ConfiguracionImpuestoRequest(new BigDecimal(porcentaje), activo);
    }

    private PedidoServicioRequest solicitudServicio(String nombre, String direccion) {
        return new PedidoServicioRequest(nombre, "3001234567", direccion, null, null,
                sucursal.getId(),
                List.of(new PedidoServicioRequest.Item(producto.getId(), 1, null, null)));
    }
}
