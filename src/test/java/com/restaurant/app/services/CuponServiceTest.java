package com.restaurant.app.services;

import com.restaurant.app.model.Cupon;
import com.restaurant.app.repository.CuponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CuponService - RF036")
class CuponServiceTest {

    private CuponRepository repository;
    private CuponService service;

    @BeforeEach
    void setUp() {
        repository = mock(CuponRepository.class);
        service = new CuponService(repository);
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
}
