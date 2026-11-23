package com.restaurant.app.controller;

import com.restaurant.app.dto.PedidoServicioRequest;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.PedidoService;
import com.restaurant.app.services.UsuarioContextService;
import com.restaurant.app.util.CatalogoPedidos;
import com.restaurant.app.util.PedidosSesionHttp;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/domicilios")
public class DomicilioController {

    private final PedidoService pedidoService;
    private final UsuarioContextService usuarioContextService;

    public DomicilioController(PedidoService pedidoService,
                               UsuarioContextService usuarioContextService) {
        this.pedidoService = pedidoService;
        this.usuarioContextService = usuarioContextService;
    }

    @PostMapping("/pedidos")
    public ResponseEntity<?> registrar(@RequestBody PedidoServicioRequest solicitud,
                                       HttpSession session) {
        try {
            Pedido pedido = pedidoService.crearPedidoDomicilio(solicitud);
            PedidosSesionHttp.registrar(session, pedido.getId());
            return ResponseEntity.ok(pedidoService.obtenerResumen(pedido.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible registrar el pedido a domicilio."));
        }
    }

    @GetMapping("/pedidos")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagoEstado,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) UUID sucursalId,
            HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!usuarioContextService.esPersonal(usuario)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para consultar domicilios."));
        }

        try {
            Collection<String> estados = normalizarEstados(estado);
            String pagoNormalizado = normalizarPagoEstado(pagoEstado);
            return ResponseEntity.ok(pedidoService.listarPedidos(
                    usuario, CatalogoPedidos.TIPO_DOMICILIO, estados, pagoNormalizado,
                    fechaDesde, fechaHasta, cliente, sucursalId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pedidos/{pedidoId}")
    public ResponseEntity<?> obtener(@PathVariable UUID pedidoId, HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        if (!usuarioContextService.esPersonal(usuario)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para consultar domicilios."));
        }
        try {
            return ResponseEntity.ok(pedidoService.obtenerPedidoServicio(
                    usuario, pedidoId, CatalogoPedidos.TIPO_DOMICILIO));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    private Collection<String> normalizarEstados(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!CatalogoPedidos.ESTADOS_PEDIDO.contains(normalizado)) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
        }
        return List.of(normalizado);
    }

    private String normalizarPagoEstado(String pagoEstado) {
        if (pagoEstado == null || pagoEstado.isBlank()) {
            return null;
        }
        String normalizado = pagoEstado.trim().toUpperCase(Locale.ROOT);
        if (!CatalogoPedidos.PAGO_PENDIENTE.equals(normalizado)
                && !CatalogoPedidos.PAGO_PAGADO.equals(normalizado)) {
            throw new IllegalArgumentException("El estado de pago no es válido.");
        }
        return normalizado;
    }

    private Usuario usuarioActual(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        return usuario instanceof Usuario u ? u : null;
    }
}
