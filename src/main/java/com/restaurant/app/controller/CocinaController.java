package com.restaurant.app.controller;

import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.PedidoService;
import com.restaurant.app.util.CatalogoPedidos;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/cocina")
public class CocinaController {

    private static final Set<String> ESTADOS_POR_DEFECTO = Set.of(
            "CONFIRMADO", "EN_PREPARACION", "LISTO");
    private static final Set<String> ROLES_COCINA = Set.of(
            "COCINERO", "ADMINISTRADOR", "SUPERADMIN");

    private final PedidoService pedidoService;

    public CocinaController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/pedidos")
    public ResponseEntity<?> listarPedidos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) UUID sucursalId,
            HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esCocinaOAdmin(usuario)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para consultar pedidos de cocina."));
        }

        Collection<String> estados;
        if (estado != null && !estado.isBlank()) {
            String normalizado = estado.trim().toUpperCase(Locale.ROOT);
            if (!CatalogoPedidos.ESTADOS_PEDIDO.contains(normalizado)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El estado del pedido no es válido."));
            }
            estados = List.of(normalizado);
        } else {
            estados = ESTADOS_POR_DEFECTO;
        }

        try {
            return ResponseEntity.ok(pedidoService.listarPedidosCocina(
                    usuario, estados, sucursalId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pedidos/{pedidoId}")
    public ResponseEntity<?> obtenerPedido(@PathVariable UUID pedidoId,
                                          HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esCocinaOAdmin(usuario)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para consultar pedidos de cocina."));
        }
        try {
            return ResponseEntity.ok(pedidoService.obtenerResumen(usuario, pedidoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/pedidos/{pedidoId}/estado")
    public ResponseEntity<?> actualizarEstado(@PathVariable UUID pedidoId,
                                              @RequestParam String estado,
                                              HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esCocinaOAdmin(usuario)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para actualizar pedidos de cocina."));
        }
        try {
            Pedido pedido = pedidoService.actualizarEstadoCocina(usuario, pedidoId, estado);
            return ResponseEntity.ok(pedidoService.obtenerResumen(pedido.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    private boolean esCocinaOAdmin(Usuario usuario) {
        return usuario.getRol() != null && ROLES_COCINA.contains(usuario.getRol());
    }

    private Usuario usuarioActual(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        return usuario instanceof Usuario u ? u : null;
    }
}
