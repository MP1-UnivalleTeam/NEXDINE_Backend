package com.restaurant.app.dto;

import com.restaurant.app.model.ConfiguracionImpuesto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConfiguracionImpuestoResponse(
        UUID id,
        UUID restauranteId,
        BigDecimal porcentaje,
        boolean activo,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        UUID modificadoPorUsuarioId,
        String modificadoPorNombre) {

    public static ConfiguracionImpuestoResponse desde(ConfiguracionImpuesto configuracion) {
        return new ConfiguracionImpuestoResponse(
                configuracion.getId(),
                configuracion.getRestauranteId(),
                configuracion.getPorcentaje(),
                configuracion.isActivo(),
                configuracion.getFechaCreacion(),
                configuracion.getFechaActualizacion(),
                configuracion.getModificadoPorUsuarioId(),
                configuracion.getModificadoPorNombre());
    }

    public static ConfiguracionImpuestoResponse porDefecto(UUID restauranteId,
                                                          BigDecimal porcentaje) {
        return new ConfiguracionImpuestoResponse(
                null, restauranteId, porcentaje, true, null, null, null, null);
    }
}
