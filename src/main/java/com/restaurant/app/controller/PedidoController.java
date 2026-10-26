package com.restaurant.app.controller;

import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.services.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping("/invitado")
    public ResponseEntity<?> crearPedidoInvitado(
            @RequestParam String nombre,
            @RequestParam String telefono,
            @RequestParam String tipoServicio,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String referencia) {

        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre es obligatorio"));
        }
        if (telefono == null || telefono.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El teléfono es obligatorio"));
        }
        if (!"DOMICILIO".equals(tipoServicio) && !"PARA_LLEVAR".equals(tipoServicio)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de servicio inválido"));
        }
        if ("DOMICILIO".equals(tipoServicio) && (direccion == null || direccion.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "La dirección es obligatoria para domicilio"));
        }

        try {
            Pedido pedido = pedidoService.crearPedidoInvitado(nombre.trim(), telefono.trim(),
                    tipoServicio, direccion, referencia);
            return ResponseEntity.ok(Map.of(
                    "id", pedido.getId(),
                    "numeroSeguimiento", pedido.getNumeroSeguimiento(),
                    "estado", pedido.getEstado()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible crear el pedido"));
        }
    }

    @PostMapping("/{pedidoId}/items")
    public ResponseEntity<?> agregarItem(
            @PathVariable UUID pedidoId,
            @RequestParam UUID productoId,
            @RequestParam int cantidad,
            @RequestParam(required = false) List<UUID> opcionIds,
            @RequestParam(required = false) String observaciones) {

        if (cantidad < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser al menos 1"));
        }

        try {
            ItemPedido item = pedidoService.agregarItem(pedidoId, productoId, cantidad, opcionIds, observaciones);
            return ResponseEntity.ok(Map.of(
                    "id", item.getId(),
                    "productoNombre", item.getProductoNombre(),
                    "cantidad", item.getCantidad()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible agregar el item"));
        }
    }

    @GetMapping("/{pedidoId}/resumen")
    public ResponseEntity<?> obtenerResumen(@PathVariable UUID pedidoId) {
        try {
            Map<String, Object> resumen = pedidoService.obtenerResumen(pedidoId);
            return ResponseEntity.ok(resumen);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible obtener el resumen"));
        }
    }

    @PostMapping("/{pedidoId}/confirmar")
    public ResponseEntity<?> confirmarPedido(
            @PathVariable UUID pedidoId,
            @RequestParam(required = false) String propinaTipo,
            @RequestParam(required = false) BigDecimal propinaValor,
            @RequestParam(required = false) String cuponCodigo) {

        try {
            Pedido pedido = pedidoService.confirmarPedido(pedidoId, propinaTipo, propinaValor, cuponCodigo);
            return ResponseEntity.ok(Map.of(
                    "id", pedido.getId(),
                    "numeroSeguimiento", pedido.getNumeroSeguimiento(),
                    "estado", pedido.getEstado(),
                    "total", pedido.getTotal()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible confirmar el pedido"));
        }
    }
}
