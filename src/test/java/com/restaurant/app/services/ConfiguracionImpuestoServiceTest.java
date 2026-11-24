package com.restaurant.app.services;

import com.restaurant.app.dto.ConfiguracionImpuestoRequest;
import com.restaurant.app.dto.ConfiguracionImpuestoResponse;
import com.restaurant.app.model.ConfiguracionImpuesto;
import com.restaurant.app.model.Empleado;
import com.restaurant.app.model.Mesero;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ConfiguracionImpuestoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfiguracionImpuestoServiceTest {

    private ConfiguracionImpuestoRepository repository;
    private UsuarioContextService usuarioContextService;
    private ConfiguracionImpuestoService service;
    private UUID restauranteId;
    private UUID sucursalId;
    private Empleado superadmin;
    private Mesero mesero;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionImpuestoRepository.class);
        usuarioContextService = mock(UsuarioContextService.class);
        service = new ConfiguracionImpuestoService(repository, usuarioContextService,
                new CalculadoraPedidoService(), BigDecimal.ZERO);

        restauranteId = UUID.randomUUID();
        sucursalId = UUID.randomUUID();

        superadmin = new Empleado();
        superadmin.setId(UUID.randomUUID());
        superadmin.setNombre("Superadmin");
        superadmin.setRol("SUPERADMIN");

        mesero = new Mesero();
        mesero.setId(UUID.randomUUID());
        mesero.setNombre("Mesero");
        mesero.setRol("MESERO");

        when(usuarioContextService.obtenerContexto(superadmin)).thenReturn(
                new UsuarioContextService.UsuarioContext(restauranteId, null, "SUPERADMIN"));
        when(usuarioContextService.obtenerContexto(mesero)).thenReturn(
                new UsuarioContextService.UsuarioContext(restauranteId, sucursalId, "MESERO"));
        when(usuarioContextService.esPersonal(any(Usuario.class))).thenAnswer(invocacion ->
                invocacion.getArgument(0) != null);
        when(repository.save(any(ConfiguracionImpuesto.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(repository.findByRestauranteId(restauranteId)).thenReturn(Optional.empty());
    }

    @Test
    void consultaDevuelveValorPorDefectoSinCrearRegistro() {
        ConfiguracionImpuestoResponse respuesta = service.consultar(superadmin);

        assertNull(respuesta.id());
        assertEquals(restauranteId, respuesta.restauranteId());
        assertEquals(0, BigDecimal.ZERO.compareTo(respuesta.porcentaje()));
        assertTrue(respuesta.activo());
        verify(repository, never()).save(any());
    }

    @Test
    void consultaExigeSesionDePersonal() {
        when(usuarioContextService.esPersonal(mesero)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.consultar(mesero));
    }

    @Test
    void soloElSuperadminPuedeActualizarLaConfiguracion() {
        assertThrows(IllegalStateException.class,
                () -> service.actualizar(mesero,
                        new ConfiguracionImpuestoRequest(new BigDecimal("19"), true)));
        verify(repository, never()).save(any());
    }

    @Test
    void soloElSuperadminPuedeCambiarElEstado() {
        assertThrows(IllegalStateException.class,
                () -> service.cambiarEstado(mesero, false));
        verify(repository, never()).save(any());
    }

    @Test
    void superadminActualizaYSeRegistraLaAuditoria() {
        ConfiguracionImpuestoResponse respuesta = service.actualizar(superadmin,
                new ConfiguracionImpuestoRequest(new BigDecimal("19"), true));

        assertEquals(new BigDecimal("19.00"), respuesta.porcentaje());
        assertTrue(respuesta.activo());
        assertEquals(superadmin.getId(), respuesta.modificadoPorUsuarioId());
        assertEquals("Superadmin", respuesta.modificadoPorNombre());
        verify(repository).save(any(ConfiguracionImpuesto.class));
    }

    @Test
    void actualizarConActivoFalseDesactivaElImpuesto() {
        ConfiguracionImpuestoResponse respuesta = service.actualizar(superadmin,
                new ConfiguracionImpuestoRequest(new BigDecimal("19"), false));

        assertFalse(respuesta.activo());
    }

    @Test
    void rechazaPorcentajesInvalidosAntesDeGuardar() {
        for (BigDecimal invalido : new BigDecimal[]{
                new BigDecimal("-1"), new BigDecimal("100.01"),
                new BigDecimal("19.999")}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.actualizar(superadmin,
                            new ConfiguracionImpuestoRequest(invalido, true)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.actualizar(superadmin,
                        new ConfiguracionImpuestoRequest(null, true)));
        verify(repository, never()).save(any());
    }

    @Test
    void tasaVigenteRespetaActivoInactivoYValorPorDefecto() {
        ConfiguracionImpuesto activa = configuracion(new BigDecimal("19.00"), true);
        when(repository.findByRestauranteId(restauranteId)).thenReturn(Optional.of(activa));
        assertEquals(0, new BigDecimal("19.00").compareTo(
                service.obtenerTasaVigente(restauranteId)));

        ConfiguracionImpuesto inactiva = configuracion(new BigDecimal("19.00"), false);
        when(repository.findByRestauranteId(restauranteId)).thenReturn(Optional.of(inactiva));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                service.obtenerTasaVigente(restauranteId)));

        when(repository.findByRestauranteId(restauranteId)).thenReturn(Optional.empty());
        assertEquals(0, BigDecimal.ZERO.compareTo(
                service.obtenerTasaVigente(restauranteId)));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.obtenerTasaVigente(null)));
    }

    @Test
    void cambiarEstadoPersisteElNuevoEstado() {
        ConfiguracionImpuesto existente = configuracion(new BigDecimal("19.00"), true);
        when(repository.findByRestauranteId(restauranteId)).thenReturn(Optional.of(existente));

        ConfiguracionImpuestoResponse respuesta = service.cambiarEstado(superadmin, false);

        assertFalse(respuesta.activo());
        assertEquals(new BigDecimal("19.00"), respuesta.porcentaje());
    }

    private ConfiguracionImpuesto configuracion(BigDecimal porcentaje, boolean activo) {
        ConfiguracionImpuesto configuracion = new ConfiguracionImpuesto();
        configuracion.setId(UUID.randomUUID());
        configuracion.setRestauranteId(restauranteId);
        configuracion.setPorcentaje(porcentaje);
        configuracion.setActivo(activo);
        return configuracion;
    }
}
