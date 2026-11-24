package com.restaurant.app.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StripePaymentServiceTest {

    @Test
    void convierteImportePesosAUnidadMenorDeStripe() {
        assertEquals(12345L,
                StripePaymentService.convertirAMinorUnits(new BigDecimal("123.45")));
    }

    @Test
    void redondeaImporteAComoMaximoDosDecimales() {
        assertEquals(101L,
                StripePaymentService.convertirAMinorUnits(new BigDecimal("1.005")));
    }

    @Test
    void rechazaImportesNoPositivos() {
        assertThrows(IllegalArgumentException.class,
                () -> StripePaymentService.convertirAMinorUnits(BigDecimal.ZERO));
    }

    @Test
    void rechazaImportesMayoresAlLimiteDeStripe() {
        assertThrows(IllegalArgumentException.class,
                () -> StripePaymentService.convertirAMinorUnits(new BigDecimal("1000000.00")));
    }
}
