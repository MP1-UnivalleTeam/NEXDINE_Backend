package com.restaurant.app.dto;

import java.math.BigDecimal;

public record ConfiguracionImpuestoRequest(BigDecimal porcentaje, Boolean activo) {
}
