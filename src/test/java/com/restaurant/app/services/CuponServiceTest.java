package com.restaurant.app.services;

import com.restaurant.app.dto.CuponRequest;
import com.restaurant.app.model.Cupon;
import com.restaurant.app.repository.CuponRepository;
import com.restaurant.app.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CuponService - RF036")
class CuponServiceTest {

    private CuponRepository repository;
    private PedidoRepository pedidos;
    private CuponService service;

    @BeforeEach
    void setUp() {
        repository = mock(CuponRepository.class);
        pedidos = mock(PedidoRepository.class);
        service = new CuponService(repository, pedidos);
    }

    private Cupon cupon(String tipo, String valor, String montoMinimo) {
        Cupon cupon = new Cupon();
        cupon.setId(UUID.randomUUID());
        cupon.setCodigo("BIENVENIDO10");
        cupon.setTipo(tipo);
        cupon.setValor(new BigDecimal(valor));
        cupon.setMontoMinimo(new BigDecimal(montoMinimo));
        cupon.setActivo(true);
        return cupon;
    }

    @Test
    @DisplayName("PORCENTAJE calcula el descuento sobre el subtotal real")
    void porcentaje() {
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10"))
                .thenReturn(Optional.of(cupon("PORCENTAJE", "10", "0")));

        var respuesta = service.validar("bienvenido10", new BigDecimal("150.00"));

        assertEquals(0, new BigDecimal("15.00").compareTo((BigDecimal) respuesta.get("descuento")));
        assertEquals("PORCENTAJE", respuesta.get("tipo"));
        assertEquals(new BigDecimal("10"), respuesta.get("valor"));
    }

    @Test
    @DisplayName("MONTO_FIJO no supera el subtotal elegible")
    void montoFijoLimitado() {
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10"))
                .thenReturn(Optional.of(cupon("MONTO_FIJO", "50", "0")));

        var respuesta = service.validar("BIENVENIDO10", new BigDecimal("30.00"));

        assertEquals(0, new BigDecimal("30.00").compareTo((BigDecimal) respuesta.get("descuento")));
    }

    @Test
    @DisplayName("Cupón inexistente o expirado se rechaza")
    void inexistente() {
        when(repository.findByCodigoIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("NOPE", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Cupón activo posterior a su fecha fin se rechaza")
    void expirado() {
        Cupon cupon = cupon("PORCENTAJE", "10", "0");
        cupon.setFechaFin(LocalDateTime.now().minusDays(1));
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10")).thenReturn(Optional.of(cupon));

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("BIENVENIDO10", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Cupón desactivado se rechaza")
    void inactivo() {
        Cupon cupon = cupon("PORCENTAJE", "10", "0");
        cupon.setActivo(false);
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10")).thenReturn(Optional.of(cupon));

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("BIENVENIDO10", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Cupón todavía no vigente se rechaza")
    void pendiente() {
        Cupon cupon = cupon("PORCENTAJE", "10", "0");
        cupon.setFechaInicio(LocalDateTime.now().plusDays(1));
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10")).thenReturn(Optional.of(cupon));

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("BIENVENIDO10", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Cupón con monto mínimo no alcanzado se rechaza")
    void montoMinimo() {
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10"))
                .thenReturn(Optional.of(cupon("PORCENTAJE", "10", "200")));

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("BIENVENIDO10", new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("Cupón sin usos disponibles se rechaza")
    void limiteUsos() {
        Cupon cupon = cupon("PORCENTAJE", "10", "0");
        cupon.setUsoMaximo(1);
        cupon.setUsos(1);
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10")).thenReturn(Optional.of(cupon));

        assertThrows(IllegalArgumentException.class,
                () -> service.validar("BIENVENIDO10", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("Descuento nulo o subtotal no positivo da cero sin lanzar error")
    void descuentoCero() {
        when(repository.findByCodigoIgnoreCase("BIENVENIDO10"))
                .thenReturn(Optional.of(cupon("PORCENTAJE", "10", "0")));

        var respuesta = service.validar("BIENVENIDO10", BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) respuesta.get("descuento")));
    }

    @Test
    @DisplayName("Crea cupón normalizando el código y conservando sus reglas")
    void crearCupon() {
        CuponRequest solicitud = new CuponRequest(
                "  verano10 ", "porcentaje", new BigDecimal("10"),
                new BigDecimal("50"), null, null, 100, true);
        when(repository.existsByCodigoIgnoreCase("VERANO10")).thenReturn(false);
        when(repository.saveAndFlush(any(Cupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cupon guardado = service.crear(solicitud);

        assertEquals("VERANO10", guardado.getCodigo());
        assertEquals("PORCENTAJE", guardado.getTipo());
        assertEquals(new BigDecimal("50"), guardado.getMontoMinimo());
        assertEquals(100, guardado.getUsoMaximo());
        verify(repository).saveAndFlush(any(Cupon.class));
    }

    @Test
    @DisplayName("Rechaza código duplicado sin crear otra fila")
    void rechazaCodigoDuplicado() {
        when(repository.existsByCodigoIgnoreCase("VERANO10")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.crear(new CuponRequest(
                        "verano10", "PORCENTAJE", BigDecimal.TEN,
                        BigDecimal.ZERO, null, null, null, true)));
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any(Cupon.class));
    }

    @Test
    @DisplayName("Rechaza valores porcentuales fuera del rango")
    void rechazaPorcentajeInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.crear(new CuponRequest(
                        "INVALIDO", "PORCENTAJE", new BigDecimal("100.01"),
                        BigDecimal.ZERO, null, null, null, true)));
    }

    @Test
    @DisplayName("Actualiza el cupón existente y permite desactivarlo")
    void actualizarYDesactivar() {
        UUID id = UUID.randomUUID();
        Cupon cupon = cupon("MONTO_FIJO", "20", "0");
        cupon.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(cupon));
        when(repository.existsByCodigoIgnoreCaseAndIdNot("NUEVO", id)).thenReturn(false);
        when(repository.saveAndFlush(any(Cupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(Cupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cupon actualizado = service.actualizar(id, new CuponRequest(
                "nuevo", "MONTO_FIJO", new BigDecimal("25"),
                BigDecimal.ZERO, null, null, null, true));
        Cupon desactivado = service.cambiarEstado(id, false);

        assertEquals(id, actualizado.getId());
        assertEquals("NUEVO", actualizado.getCodigo());
        assertFalse(desactivado.isActivo());
    }

    @Test
    @DisplayName("Elimina cupones sin usos ni pedidos relacionados")
    void eliminaCuponSinHistorial() {
        UUID id = UUID.randomUUID();
        Cupon cupon = cupon("MONTO_FIJO", "20", "0");
        cupon.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(cupon));
        when(pedidos.existsByCuponCodigoIgnoreCase(cupon.getCodigo())).thenReturn(false);

        service.eliminar(id);

        verify(repository).delete(cupon);
    }

    @Test
    @DisplayName("Conserva cupones con historial de pedidos")
    void conservaCuponConHistorial() {
        UUID id = UUID.randomUUID();
        Cupon cupon = cupon("PORCENTAJE", "10", "0");
        cupon.setId(id);
        cupon.setUsos(1);
        when(repository.findById(id)).thenReturn(Optional.of(cupon));

        assertThrows(IllegalStateException.class, () -> service.eliminar(id));
        verify(repository, org.mockito.Mockito.never()).delete(cupon);
    }
}
