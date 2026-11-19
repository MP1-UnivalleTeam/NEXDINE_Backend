package com.restaurant.app.dto;

import java.util.List;
import java.util.UUID;

public record PedidoServicioRequest(
        String nombre,
        String telefono,
        String direccion,
        String referencia,
        String observaciones,
        UUID sucursalId,
        List<Item> items) {

    public record Item(UUID productoId, int cantidad, List<UUID> opcionIds, String observaciones) {
    }
}
