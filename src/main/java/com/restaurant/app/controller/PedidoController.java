package com.restaurant.app.controller;

import com.restaurant.app.dto.PedidoManualRequest;
import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.PedidoEventosService;
import com.restaurant.app.services.PedidoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final String PEDIDOS_INVITADO_SESSION = "pedidosInvitado";
    private static final int MAX_PEDIDOS_INVITADO_SESSION = 50;

    private final PedidoService pedidoService;
    private final PedidoEventosService eventosService;

    public PedidoController(PedidoService pedidoService, PedidoEventosService eventosService) {
        this.pedidoService = pedidoService;
        this.eventosService = eventosService;
    }

    @PostMapping("/manual")
    public ResponseEntity<?> crearPedidoManual(@RequestBody PedidoManualRequest solicitud,
                                               HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esMeseroOAdmin(usuario)) {
            return ResponseEntity.status(403).body(Map.of("error", "No tiene permisos para registrar pedidos."));
        }
        try {
            Pedido pedido = pedidoService.crearPedidoManual(usuario, solicitud);
            return ResponseEntity.ok(pedidoService.obtenerResumen(pedido.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible registrar el pedido manual"));
        }
    }

    @GetMapping("/activos")
    public ResponseEntity<?> listarActivos(HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esPersonal(usuario)) {
            return ResponseEntity.status(403).body(Map.of("error", "No tiene permisos para consultar pedidos."));
        }
        try {
            return ResponseEntity.ok(pedidoService.listarPedidosActivos(usuario));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mesas/{mesaId}/pedidos")
    public ResponseEntity<?> listarPedidosDeMesa(@PathVariable UUID mesaId, HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esPersonal(usuario)) {
            return ResponseEntity.status(403).body(Map.of("error", "No tiene permisos para consultar pedidos."));
        }
        try {
            return ResponseEntity.ok(pedidoService.listarPedidosDeMesa(usuario, mesaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{pedidoId}/estado")
    public ResponseEntity<?> actualizarEstado(@PathVariable UUID pedidoId,
                                              @RequestParam String estado,
                                              HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esPersonal(usuario)) {
            return ResponseEntity.status(403).body(Map.of("error", "No tiene permisos para actualizar pedidos."));
        }
        try {
            Pedido pedido = pedidoService.actualizarEstado(usuario, pedidoId, estado);
            return ResponseEntity.ok(pedidoService.obtenerResumen(pedido.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/activos/eventos")
    public ResponseEntity<?> escucharCambios(HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!esPersonal(usuario)) {
            return ResponseEntity.status(403).body(Map.of("error", "No tiene permisos para consultar pedidos."));
        }
        try {
            List<UUID> sucursalIds = pedidoService.sucursalesParaEventos(usuario);
            SseEmitter emitter = eventosService.suscribir(sucursalIds);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invitado")
    public ResponseEntity<?> crearPedidoInvitado(
            @RequestParam String nombre,
            @RequestParam String telefono,
            @RequestParam String tipoServicio,
            @RequestParam(required = false) String direccion,
            @RequestParam(required = false) String referencia,
            @RequestParam(required = false) UUID sucursalId,
            HttpSession session) {

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
                    tipoServicio, direccion, referencia, sucursalId);
            registrarPedidoInvitado(session, pedido.getId());
            return ResponseEntity.ok(Map.of(
                    "id", pedido.getId(),
                    "numeroSeguimiento", pedido.getNumeroSeguimiento(),
                    "estado", pedido.getEstado()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            @RequestParam(required = false) String observaciones,
            HttpSession session) {

        if (!esPedidoDeSesion(pedidoId, session)) {
            return ResponseEntity.notFound().build();
        }
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
    public ResponseEntity<?> obtenerResumen(@PathVariable UUID pedidoId, HttpSession session) {
        Usuario usuario = usuarioActual(session);
        boolean esInvitadoPropietario = esPedidoDeSesion(pedidoId, session);
        if (!esInvitadoPropietario && (usuario == null || !esPersonal(usuario))) {
            return ResponseEntity.notFound().build();
        }
        try {
            Map<String, Object> resumen = esInvitadoPropietario
                    ? pedidoService.obtenerResumen(pedidoId)
                    : pedidoService.obtenerResumen(usuario, pedidoId);
            return ResponseEntity.ok(resumen);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible obtener el resumen"));
        }
    }

    @PostMapping("/{pedidoId}/confirmar")
    public ResponseEntity<?> confirmarPedido(
            @PathVariable UUID pedidoId,
            @RequestParam(required = false) String propinaTipo,
            @RequestParam(required = false) BigDecimal propinaValor,
            @RequestParam(required = false) BigDecimal propinaPorcentaje,
            @RequestParam(required = false) String cuponCodigo,
            HttpSession session) {

        if (!esPedidoDeSesion(pedidoId, session)) {
            return ResponseEntity.notFound().build();
        }
        try {
            Pedido pedido = pedidoService.confirmarPedido(pedidoId, propinaTipo, propinaValor,
                    propinaPorcentaje, cuponCodigo);
            return ResponseEntity.ok(pedidoService.obtenerResumen(pedido.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No fue posible confirmar el pedido"));
        }
    }

    private Usuario usuarioActual(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        return usuario instanceof Usuario u ? u : null;
    }

    private boolean esPersonal(Usuario usuario) {
        return usuario.getRol() != null
                && List.of("MESERO", "COCINERO", "ADMINISTRADOR", "SUPERADMIN")
                .contains(usuario.getRol());
    }

    private boolean esMeseroOAdmin(Usuario usuario) {
        return usuario.getRol() != null
                && List.of("MESERO", "ADMINISTRADOR", "SUPERADMIN").contains(usuario.getRol());
    }

    private void registrarPedidoInvitado(HttpSession session, UUID pedidoId) {
        Object pedidos = session.getAttribute(PEDIDOS_INVITADO_SESSION);
        Set<UUID> ids = new LinkedHashSet<>();
        if (pedidos instanceof Set<?> existentes) {
            existentes.stream()
                    .filter(UUID.class::isInstance)
                    .map(UUID.class::cast)
                    .forEach(ids::add);
        }
        ids.add(pedidoId);
        while (ids.size() > MAX_PEDIDOS_INVITADO_SESSION) {
            ids.remove(ids.iterator().next());
        }
        session.setAttribute(PEDIDOS_INVITADO_SESSION, ids);
    }

    private boolean esPedidoDeSesion(UUID pedidoId, HttpSession session) {
        Object pedidos = session.getAttribute(PEDIDOS_INVITADO_SESSION);
        return pedidos instanceof Set<?> ids && ids.contains(pedidoId);
    }
}
