package com.restaurant.app.services;

import com.restaurant.app.model.ItemPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraPedidoServiceTest {

    private final CalculadoraPedidoService calculadora = new CalculadoraPedidoService();

    @Test
    void aplicaDescuentoAntesDelImpuestoYCalculaPropinaSobreSubtotal() {
        CalculadoraPedidoService.Desglose desglose = calculadora.calcular(
                new BigDecimal("200.00"), new BigDecimal("20.00"),
                new BigDecimal("19.00"), "PORCENTAJE", new BigDecimal("10.00"),
                null, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("180.00").compareTo(desglose.baseGravable()));
        assertEquals(0, new BigDecimal("34.20").compareTo(desglose.impuestos()));
        assertEquals(0, new BigDecimal("20.00").compareTo(desglose.propina()));
        assertEquals(0, new BigDecimal("234.20").compareTo(desglose.total()));
    }

    @Test
    void redondeaMitadHaciaArribaEnLaMoneda() {
        CalculadoraPedidoService.Desglose desglose = calculadora.calcular(
                new BigDecimal("10.05"), BigDecimal.ZERO,
                new BigDecimal("10.00"), "NINGUNA", null, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("1.01").compareTo(desglose.impuestos()));
        assertEquals(0, new BigDecimal("11.06").compareTo(desglose.total()));
    }

    @Test
    void elDescuentoNuncaSuperaElSubtotalNiLaBaseEsNegativa() {
        CalculadoraPedidoService.Desglose desglose = calculadora.calcular(
                new BigDecimal("80.00"), new BigDecimal("500.00"),
                new BigDecimal("19.00"), "NINGUNA", null, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("80.00").compareTo(desglose.descuento()));
        assertEquals(0, BigDecimal.ZERO.compareTo(desglose.baseGravable()));
        assertEquals(0, BigDecimal.ZERO.compareTo(desglose.impuestos()));
        assertEquals(0, BigDecimal.ZERO.compareTo(desglose.total()));
    }

    @Test
    void unaTasaNulaSeTrataComoCero() {
        CalculadoraPedidoService.Desglose desglose = calculadora.calcular(
                new BigDecimal("100.00"), BigDecimal.ZERO,
                null, "NINGUNA", null, BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(0, BigDecimal.ZERO.compareTo(desglose.impuestos()));
        assertEquals(0, new BigDecimal("100.00").compareTo(desglose.total()));
    }

    @Test
    void sumaAdicionalesDeOpcionesPorCantidad() {
        ItemPedido item = new ItemPedido();
        item.setPrecioUnitario(new BigDecimal("10.00"));
        item.setAdicionalOpciones(new BigDecimal("2.50"));
        item.setCantidad(3);

        assertEquals(0, new BigDecimal("37.50").compareTo(
                calculadora.calcularSubtotal(List.of(item))));
    }

    @Test
    void unPedidoSinItemsTieneSubtotalCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculadora.calcularSubtotal(List.of())));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculadora.calcularSubtotal(null)));
    }

    @Test
    void rechazaPorcentajesInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.validarPorcentaje(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.validarPorcentaje(new BigDecimal("100.01")));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.validarPorcentaje(new BigDecimal("19.999")));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.validarPorcentaje(null));
    }

    @Test
    void aceptaLosLimitesCeroYCien() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculadora.validarPorcentaje(BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                calculadora.validarPorcentaje(new BigDecimal("100"))));
    }

    @Test
    void rechazaTasasFueraDeRangoAlCalcular() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> calculadora.calcular(
                new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("-1"), "NINGUNA", null, BigDecimal.ZERO, BigDecimal.ZERO))
                .getMessage().contains("tasa"));
    }
}
