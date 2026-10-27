package com.restaurant.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CuponRequest(
        String codigo,
        String tipo,
        BigDecimal valor,
        BigDecimal montoMinimo,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Integer usoMaximo,
        Boolean activo) {
}
