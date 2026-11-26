package com.restaurant.app.services;

import com.restaurant.app.model.Administrador;
import com.restaurant.app.model.AdministradorSucursal;
import com.restaurant.app.model.Cliente;
import com.restaurant.app.model.HistorialPedido;
import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Recordatorio;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.ConfiguracionFacturacionRepository;
import com.restaurant.app.repository.FacturaRepository;
import com.restaurant.app.repository.HistorialPedidoRepository;
import com.restaurant.app.repository.HorarioSucursalRepository;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.RecordatorioRepository;
import com.restaurant.app.repository.ReembolsoRepository;
import com.restaurant.app.repository.ResumenVentaDiariaRepository;
import com.restaurant.app.repository.RestauranteRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class Sprint4y5IntegracionTest {

    @Autowired private RestauranteRepository restaurantes;
    @Autowired private SucursalRepository sucursales;
    @Autowired private ProductoRepository productos;
    @Autowired private ProductoSucursalRepository productoSucursales;
    @Autowired private PedidoRepository pedidos;
    @Autowired private ItemPedidoRepository items;
    @Autowired private HistorialPedidoRepository historial;
    @Autowired private UserRepository usuarios;
    @Autowired private AdministradorSucursalRepository adminSucursales;
    @Autowired private HorarioSucursalRepository horarios;
    @Autowired private ConfiguracionFacturacionRepository configuraciones;
    @Autowired private FacturaRepository facturas;
    @Autowired private ReembolsoRepository reembolsos;
    @Autowired private ResumenVentaDiariaRepository resumenes;
    @Autowired private RecordatorioRepository recordatorios;

    @Autowired private PedidoService pedidoService;
    @Autowired private HorarioService horarioService;
    @Autowired private FacturacionService facturacionService;
    @Autowired private ReembolsoService reembolsoService;
    @Autowired private ReporteService reporteService;
    @Autowired private RecordatorioService recordatorioService;

    private Restaurante restaurante;
    private Sucursal sucursal;
    private Producto producto;
    private Administrador admin;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        restaurante = new Restaurante();
        restaurante.setNombre("Restaurante Sprint " + UUID.randomUUID());
        restaurante.setActivo(true);
        restaurantes.save(restaurante);

        sucursal = new Sucursal();
        sucursal.setRestauranteId(restaurante.getId());
        sucursal.setNombre("Sucursal Sprint");
        sucursal.setActiva(true);
        sucursales.save(sucursal);

        producto = new Producto();
        producto.setNombre("Hamburguesa");
        producto.setPrecio(new BigDecimal("20000.00"));
        producto.setRestauranteId(restaurante.getId());
        producto.setDisponible(true);
        productos.save(producto);
        productoSucursales.save(new ProductoSucursal(producto.getId(), sucursal.getId(), restaurante.getId()));

        admin = new Administrador();
        admin.setNombre("Admin Sprint " + UUID.randomUUID());
        admin.setRol("ADMINISTRADOR");
        admin.setEstado(true);
        usuarios.save(admin);
        adminSucursales.save(new AdministradorSucursal(admin.getId(), sucursal.getId()));

        cliente = new Cliente();
        cliente.setNombre("Cliente Sprint " + UUID.randomUUID());
        cliente.setRol("CLIENTE");
        cliente.setCorreo("cliente@example.com");
        cliente.setEstado(true);
        usuarios.save(cliente);
    }

    @AfterEach
    void limpiar() {
        recordatorios.deleteAll();
        resumenes.deleteAll();
        reembolsos.deleteAll();
        facturas.deleteAll();
        configuraciones.deleteAll();
        horarios.deleteAll();
        historial.deleteAll();
        items.deleteAll();
        pedidos.deleteAll();
        adminSucursales.deleteAll();
        usuarios.deleteById(admin.getId());
        usuarios.deleteById(cliente.getId());
        productoSucursales.deleteAll();
        productos.deleteAll();
        sucursales.deleteAll();
        restaurantes.deleteAll();
    }

    private Pedido pedidoConfirmado(UUID clienteId) {
        Pedido pedido = pedidoService.crearPedidoInvitado("Ana", "3001234567", "PARA_LLEVAR",
                null, null, sucursal.getId(), null, clienteId, "ana@example.com");
        pedidoService.agregarItem(pedido.getId(), producto.getId(), 2, null, null);
        return pedidoService.confirmarPedido(pedido.getId(), null, null, null);
    }

    // ------------------------------------------------------------ RF009 / RF033

    @Test
    void cancelarExigeMotivoYGuardaElMotivoEnPedidoEHistorial() {
        Pedido pedido = pedidoConfirmado(null);

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.cancelarPedido(admin, pedido.getId(), "  "));

        pedidoService.cancelarPedido(admin, pedido.getId(), "El cliente ya no lo quiere");
        Pedido cancelado = pedidos.findById(pedido.getId()).orElseThrow();
        assertEquals("CANCELADO", cancelado.getEstado());
        assertEquals("El cliente ya no lo quiere", cancelado.getMotivoCancelacion());
        assertNotNull(cancelado.getFechaCancelacion());
        assertTrue(historial.findAll().stream()
                .filter(h -> h.getPedidoId().equals(pedido.getId()))
                .map(HistorialPedido::getMotivo)
                .anyMatch("El cliente ya no lo quiere"::equals));
    }

    @Test
    void noSePuedeCancelarUnPedidoEnPreparacion() {
        Pedido pedido = pedidoConfirmado(null);
        pedidoService.actualizarEstado(admin, pedido.getId(), "EN_PREPARACION");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pedidoService.cancelarPedido(admin, pedido.getId(), "Motivo cualquiera"));
        assertTrue(error.getMessage().contains("preparación"));
    }

    @Test
    void elClientePuedeCancelarSuPedidoPendiente() {
        Pedido pedido = pedidoService.crearPedidoInvitado("Ana", "3001234567", "PARA_LLEVAR",
                null, null, sucursal.getId());
        pedidoService.cancelarPedidoPropio(pedido.getId(), "Me equivoqué de dirección");
        assertEquals("CANCELADO", pedidos.findById(pedido.getId()).orElseThrow().getEstado());
    }

    // ------------------------------------------------------------ RF010

    @Test
    void historialDelClienteSoloIncluyeSusPedidos() {
        Pedido propio = pedidoConfirmado(cliente.getId());
        pedidoConfirmado(null);

        List<Map<String, Object>> historialCliente = pedidoService.listarPedidosCliente(cliente.getId());
        assertEquals(1, historialCliente.size());
        assertEquals(propio.getId(), historialCliente.get(0).get("id"));
        assertTrue(pedidoService.esPedidoDelCliente(propio.getId(), cliente.getId()));
    }

    // ------------------------------------------------------------ RF044

    @Test
    void sucursalCerradaNoRecibePedidos() {
        List<Map<String, Object>> semana = new ArrayList<>();
        LocalTime ahora = horarioService.ahora().toLocalTime();
        for (int dia = 1; dia <= 7; dia++) {
            Map<String, Object> horario = new HashMap<>();
            horario.put("diaSemana", dia);
            // Una ventana de un minuto que ya pasó o aún no llega: cerrado ahora.
            horario.put("horaApertura", ahora.plusHours(2).withSecond(0).withNano(0).toString());
            horario.put("horaCierre", ahora.plusHours(2).plusMinutes(1).withSecond(0).withNano(0).toString());
            horario.put("activo", true);
            semana.add(horario);
        }
        Map<String, Object> resultado = horarioService.guardar(admin, sucursal.getId(), semana);
        assertEquals(false, resultado.get("abiertoAhora"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> pedidoService.crearPedidoInvitado("Ana", "3001234567", "PARA_LLEVAR",
                        null, null, sucursal.getId()));
        assertTrue(error.getMessage().contains("cerrada"));
    }

    @Test
    void horarioQueCruzaLaMedianocheSigueAbiertoDeMadrugada() {
        com.restaurant.app.model.HorarioSucursal viernes = new com.restaurant.app.model.HorarioSucursal();
        viernes.setDiaSemana(5);
        viernes.setHoraApertura(LocalTime.of(18, 0));
        viernes.setHoraCierre(LocalTime.of(2, 0));
        viernes.setActivo(true);
        // Sábado 1:30 a. m. pertenece al turno del viernes.
        LocalDateTime sabadoMadrugada = LocalDateTime.of(2026, 11, 28, 1, 30);
        assertTrue(HorarioService.estaAbierto(List.of(viernes), sabadoMadrugada));
        assertFalse(HorarioService.estaAbierto(List.of(viernes), sabadoMadrugada.plusHours(1)));
    }

    // ------------------------------------------------------------ RF030

    @Test
    void facturaUsaElConsecutivoYEsUnaPorPedido() {
        Pedido pedido = pedidoConfirmado(null);
        pedidoService.actualizarEstado(admin, pedido.getId(), "EN_PREPARACION");
        pedidoService.actualizarEstado(admin, pedido.getId(), "LISTO");

        assertThrows(IllegalStateException.class,
                () -> facturacionService.emitir(admin, pedido.getId(), Map.of()));

        facturacionService.guardarEmisor(admin, Map.of(
                "razonSocial", "Restaurante Demo SAS", "nit", "900123456-7", "prefijo", "fe"));
        Map<String, Object> primera = facturacionService.emitir(admin, pedido.getId(),
                Map.of("nombre", "Ana", "tipoDocumento", "CC", "documento", "123"));
        assertEquals("FE-1", primera.get("numero"));

        Map<String, Object> repetida = facturacionService.emitir(admin, pedido.getId(), Map.of());
        assertEquals("FE-1", repetida.get("numero"));
        assertEquals(true, repetida.get("yaEmitida"));
    }

    // ------------------------------------------------------------ RF032

    @Test
    void reembolsoNequiNoSuperaLoPagado() {
        Pedido pedido = pedidoConfirmado(null);
        Pedido pagado = pedidos.findById(pedido.getId()).orElseThrow();
        pagado.setPagoEstado(CatalogoPedidos.PAGO_PAGADO);
        pagado.setPagoMetodo(CatalogoPedidos.METODO_TRANSFERENCIA);
        pedidos.save(pagado);
        pedidoService.cancelarPedido(admin, pedido.getId(), "Producto agotado");

        BigDecimal total = pedidos.findById(pedido.getId()).orElseThrow().getTotal();
        assertThrows(IllegalArgumentException.class, () -> reembolsoService.crear(admin, pedido.getId(),
                Map.of("metodo", "NEQUI", "destino", "3001234567", "monto", total.add(BigDecimal.ONE).toPlainString())));
        assertThrows(IllegalArgumentException.class, () -> reembolsoService.crear(admin, pedido.getId(),
                Map.of("metodo", "NEQUI", "destino", "123", "monto", "1000")));

        Map<String, Object> reembolso = reembolsoService.crear(admin, pedido.getId(),
                Map.of("metodo", "NEQUI", "destino", "300 123 4567", "monto", total.toPlainString()));
        assertEquals("EN_PROCESO", reembolso.get("estado"));
        assertEquals("3001234567", reembolso.get("destino"));

        Map<String, Object> completado = reembolsoService.actualizarEstado(
                admin, (UUID) reembolso.get("id"), "COMPLETADO", "NEQUI-778");
        assertEquals("COMPLETADO", completado.get("estado"));
    }

    // ------------------------------------------------------------ RF017 / RF021 / RF042

    @Test
    void reporteYRegistroDiarioCuentanSoloVentasConfirmadas() {
        pedidoConfirmado(null);
        Pedido cancelado = pedidoConfirmado(null);
        pedidoService.cancelarPedido(admin, cancelado.getId(), "Cliente se fue");
        pedidoService.crearPedidoInvitado("Pendiente", "300", "PARA_LLEVAR", null, null, sucursal.getId());

        LocalDate hoy = LocalDate.now();
        Map<String, Object> reporte = reporteService.reporteVentas(admin, "DIARIO", hoy, hoy, null);
        assertEquals(1, reporte.get("numeroPedidos"));
        assertEquals(1, reporte.get("pedidosCancelados"));
        assertEquals(0, new BigDecimal("40000.00").compareTo((BigDecimal) reporte.get("totalVentas")));

        reporteService.registrarDia(hoy);
        List<Map<String, Object>> registro = reporteService.registroDiario(admin, hoy, hoy, null);
        assertEquals(1, registro.size());
        assertEquals(1, registro.get(0).get("numeroPedidos"));
    }

    // ------------------------------------------------------------ RF027

    @Test
    void recordatorioDiarioSeDisparaUnaVezPorOcurrencia() {
        LocalDateTime ahora = recordatorioService.ahora();
        Recordatorio r = new Recordatorio();
        r.setRestauranteId(restaurante.getId());
        r.setTipo("CIERRE_CAJA");
        r.setTitulo("Cierre de caja");
        r.setFrecuencia("DIARIA");
        r.setHora(ahora.minusMinutes(1).toLocalTime().withSecond(0).withNano(0));
        r.setCanales("APP");
        r.setCreadoEn(LocalDateTime.now().minusDays(1));
        recordatorios.save(r);

        assertEquals(1, recordatorioService.dispararPendientes());
        assertEquals(0, recordatorioService.dispararPendientes());
        assertEquals(1, recordatorioService.avisos(admin, ahora.minusMinutes(5)).size());
    }

    @Test
    void ultimaOcurrenciaSemanalUsaDomingoComoCero() {
        Recordatorio r = new Recordatorio();
        r.setFrecuencia("SEMANAL");
        r.setDiasSemana("0");
        r.setHora(LocalTime.of(9, 0));
        // Miércoles 25/11/2026 → el domingo anterior fue el 22.
        LocalDateTime ocurrencia = RecordatorioService.ultimaOcurrencia(r, LocalDateTime.of(2026, 11, 25, 12, 0));
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 0), ocurrencia);
    }

    /**
     * En Supabase (migración V11) dia_semana y dia_mes son SMALLINT. Con
     * HIBERNATE_DDL_AUTO=validate el backend no arranca si la entidad las
     * mapea como INTEGER, así que el tipo se comprueba aquí.
     */
    @Test
    void columnasSmallintCoincidenConLaMigracion() {
        for (String[] columna : new String[][]{{"horarios_sucursal", "dia_semana"}, {"recordatorios", "dia_mes"}}) {
            String tipo = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns WHERE lower(table_name) = ? AND lower(column_name) = ?",
                    String.class, columna[0], columna[1]);
            assertEquals("SMALLINT", tipo.toUpperCase(), columna[0] + "." + columna[1]);
        }
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @SuppressWarnings("unused")
    private List<ItemPedido> itemsDe(UUID pedidoId) {
        return items.findByPedidoId(pedidoId);
    }
}
