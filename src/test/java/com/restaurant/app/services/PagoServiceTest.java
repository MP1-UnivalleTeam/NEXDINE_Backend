package com.restaurant.app.services;

import com.restaurant.app.model.Mesero;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoServiceTest {

    private PedidoRepository pedidoRepository;
    private PedidoService pedidoService;
    private MesaService mesaService;
    private UsuarioContextService usuarioContextService;
    private NotificacionService notificacionService;
    private SucursalRepository sucursalRepository;
    private PagoService pagoService;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        pedidoService = mock(PedidoService.class);
        mesaService = mock(MesaService.class);
        usuarioContextService = mock(UsuarioContextService.class);
        notificacionService = mock(NotificacionService.class);
        sucursalRepository = mock(SucursalRepository.class);

        pagoService = new PagoService(pedidoRepository, pedidoService, mesaService,
                usuarioContextService, notificacionService, sucursalRepository);
    }

    private Pedido pedido(String tipoServicio, String pagoEstado) {
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setNumeroSeguimiento("NX-TEST-0001");
        pedido.setTipoServicio(tipoServicio);
        pedido.setEstado("CONFIRMADO");
        pedido.setPagoEstado(pagoEstado);
        pedido.setTotal(new BigDecimal("100.00"));
        pedido.setSucursalId(UUID.randomUUID());
        return pedido;
    }

    @Test
    void contraEntregaSoloAplicaADomicilio() {
        Pedido pedido = pedido("PARA_LLEVAR", "PENDIENTE");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> pagoService.solicitarMetodoPago(null, pedido.getId(), "CONTRA_ENTREGA"));

        assertTrue(excepcion.getMessage().contains("domicilio"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void contraEntregaEnDomicilioQuedaPendiente() {
        Pedido pedido = pedido("DOMICILIO", "PENDIENTE");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido guardado = pagoService.solicitarMetodoPago(null, pedido.getId(), "contra entrega");

        assertEquals("CONTRA_ENTREGA", guardado.getPagoMetodo());
        assertEquals("PENDIENTE", guardado.getPagoEstado());
    }

    @Test
    void montoInsuficienteNoSeRegistra() {
        Pedido pedido = pedido("DOMICILIO", "PENDIENTE");
        pedido.setPagoMetodo("CONTRA_ENTREGA");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(pedidoService.obtenerTotalCalculado(pedido.getId()))
                .thenReturn(new BigDecimal("100.00"));

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrarMontoEfectivo(
                        null, pedido.getId(), new BigDecimal("50.00")));

        assertTrue(excepcion.getMessage().contains("insuficiente"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void montoEfectivoCalculaCambioYPermanecePendiente() {
        Pedido pedido = pedido("DOMICILIO", "PENDIENTE");
        pedido.setPagoMetodo("CONTRA_ENTREGA");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(pedidoService.obtenerTotalCalculado(pedido.getId()))
                .thenReturn(new BigDecimal("70.00"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido guardado = pagoService.registrarMontoEfectivo(
                null, pedido.getId(), new BigDecimal("100.00"));

        assertEquals(0, new BigDecimal("100.00").compareTo(guardado.getPagoImporteRecibido()));
        assertEquals(0, new BigDecimal("30.00").compareTo(guardado.getPagoCambio()));
        assertEquals("PENDIENTE", guardado.getPagoEstado());
    }

    @Test
    void confirmarPagoMarcaPagadoLiberaMesaYNotificaUnaSolaVez() {
        Pedido pedido = pedido("MESA", "PENDIENTE");
        pedido.setMesaId(UUID.randomUUID());
        pedido.setPagoImporteRecibido(new BigDecimal("100.00"));
        Sucursal sucursal = sucursal(pedido.getSucursalId());
        when(sucursalRepository.findById(pedido.getSucursalId()))
                .thenReturn(Optional.of(sucursal));

        Mesero mesero = mesero(pedido.getSucursalId());
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioContextService.obtenerContexto(mesero)).thenReturn(
                new UsuarioContextService.UsuarioContext(
                        sucursal.getRestauranteId(), pedido.getSucursalId(), "MESERO"));

        Pedido guardado = pagoService.confirmarPago(mesero, pedido.getId());

        assertEquals("PAGADO", guardado.getPagoEstado());
        verify(mesaService).liberarMesaSiCorresponde(pedido.getMesaId());
        verify(notificacionService).registrarPagoExitoso(any(), any());
        verify(notificacionService).publicarPagoExitoso(any(), any());

        assertThrows(IllegalArgumentException.class,
                () -> pagoService.confirmarPago(mesero, pedido.getId()));
    }

    @Test
    void confirmarPagoRequierePersonalAutorizado() {
        Pedido pedido = pedido("MESA", "PENDIENTE");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> pagoService.confirmarPago(null, pedido.getId()));
    }

    @Test
    void noPermiteConfirmarPagoDePedidoCancelado() {
        Pedido pedido = pedido("MESA", "PENDIENTE");
        pedido.setEstado("CANCELADO");
        pedido.setMesaId(UUID.randomUUID());
        Sucursal sucursal = sucursal(pedido.getSucursalId());
        Mesero mesero = mesero(pedido.getSucursalId());
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(sucursalRepository.findById(pedido.getSucursalId()))
                .thenReturn(Optional.of(sucursal));
        when(usuarioContextService.obtenerContexto(mesero)).thenReturn(
                new UsuarioContextService.UsuarioContext(
                        sucursal.getRestauranteId(), pedido.getSucursalId(), "MESERO"));

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> pagoService.confirmarPago(mesero, pedido.getId()));

        assertTrue(excepcion.getMessage().contains("cancelado"));
        assertEquals("PENDIENTE", pedido.getPagoEstado());
        verify(pedidoRepository, never()).save(any());
        verify(notificacionService, never()).registrarPagoExitoso(any(), any());
    }

    @Test
    void noPermiteRegistrarMontoEnPedidoCancelado() {
        Pedido pedido = pedido("DOMICILIO", "PENDIENTE");
        pedido.setEstado("CANCELADO");
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> pagoService.registrarMontoEfectivo(
                        null, pedido.getId(), new BigDecimal("100.00")));

        assertTrue(excepcion.getMessage().contains("cancelado"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelarPagoAprobadoLimpiaPagoSinCancelarPedido() {
        Pedido pedido = pedido("MESA", "PAGADO");
        pedido.setMesaId(UUID.randomUUID());
        pedido.setPagoMetodo("EFECTIVO");
        pedido.setPagoImporteRecibido(new BigDecimal("100.00"));
        pedido.setPagoCambio(BigDecimal.ZERO);
        Sucursal sucursal = sucursal(pedido.getSucursalId());
        Mesero mesero = mesero(pedido.getSucursalId());
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(sucursalRepository.findById(pedido.getSucursalId())).thenReturn(Optional.of(sucursal));
        when(usuarioContextService.obtenerContexto(mesero)).thenReturn(
                new UsuarioContextService.UsuarioContext(
                        sucursal.getRestauranteId(), pedido.getSucursalId(), "MESERO"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido guardado = pagoService.cancelarPago(mesero, pedido.getId());

        assertEquals("CANCELADO", guardado.getPagoEstado());
        assertEquals("", guardado.getPagoMetodo());
        assertEquals(BigDecimal.ZERO, guardado.getPagoImporteRecibido());
        assertEquals(BigDecimal.ZERO, guardado.getPagoCambio());
        assertEquals("CONFIRMADO", guardado.getEstado());
        verify(pedidoRepository).save(any(Pedido.class));
        verify(mesaService).ocuparMesa(pedido.getMesaId());
    }

    @Test
    void cancelarPagoConTarjetaNoSeCancelaLocalmente() {
        Pedido pedido = pedido("DOMICILIO", "PENDIENTE");
        pedido.setPagoMetodo("TARJETA");
        pedido.setPagoImporteRecibido(BigDecimal.ZERO);
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class,
                () -> pagoService.cancelarPago(null, pedido.getId()));

        assertTrue(excepcion.getMessage().contains("cancelar un pago local"));
        verify(pedidoRepository, never()).save(any());
    }

    private Sucursal sucursal(UUID sucursalId) {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(sucursalId);
        sucursal.setRestauranteId(UUID.randomUUID());
        sucursal.setActiva(true);
        return sucursal;
    }

    private Mesero mesero(UUID sucursalId) {
        Mesero mesero = new Mesero();
        mesero.setId(UUID.randomUUID());
        mesero.setNombre("Mesero Test");
        mesero.setRol("MESERO");
        mesero.setSucursalId(sucursalId);
        return mesero;
    }
}
