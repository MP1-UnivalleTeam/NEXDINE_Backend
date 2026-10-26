package com.restaurant.app.dto;

import java.util.List;
import java.util.UUID;

public record PedidoManualRequest(
        UUID mesaId,
        String nombre,
        String telefono,
        String observaciones,
        List<Item> items) {

    public record Item(UUID productoId, int cantidad, String observaciones) {
    }
}
