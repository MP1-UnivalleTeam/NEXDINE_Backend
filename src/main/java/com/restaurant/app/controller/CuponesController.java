package com.restaurant.app.controller;

import com.restaurant.app.dto.CuponRequest;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CuponService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/cupones")
public class CuponesController {

    private final CuponService cuponService;

    public CuponesController(CuponService cuponService) {
        this.cuponService = cuponService;
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validarCupon(@RequestParam String codigo,
                                          @RequestParam(required = false) BigDecimal subtotal) {
        try {
            return ResponseEntity.ok(cuponService.validar(codigo, subtotal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible validar el cupón."));
        }
    }

    @GetMapping("/admin")
    public ResponseEntity<?> listarCupones(HttpSession session) {
        if (!esAdministrador(session)) {
            return ResponseEntity.status(session.getAttribute("currentUser") == null ? 401 : 403).build();
        }
        return ResponseEntity.ok(cuponService.listarAdministracion());
    }

    @PostMapping("/admin")
    public ResponseEntity<?> crearCupon(@RequestBody CuponRequest solicitud, HttpSession session) {
        if (!esAdministrador(session)) {
            return ResponseEntity.status(session.getAttribute("currentUser") == null ? 401 : 403).build();
        }
        try {
            return ResponseEntity.ok(cuponService.crear(solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/admin/{id}")
    public ResponseEntity<?> actualizarCupon(@PathVariable String id,
                                              @RequestBody CuponRequest solicitud,
                                              HttpSession session) {
        if (!esAdministrador(session)) {
            return ResponseEntity.status(session.getAttribute("currentUser") == null ? 401 : 403).build();
        }
        try {
            return ResponseEntity.ok(cuponService.actualizar(
                    java.util.UUID.fromString(id), solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/admin/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable String id,
                                           @RequestParam boolean activo,
                                           HttpSession session) {
        if (!esAdministrador(session)) {
            return ResponseEntity.status(session.getAttribute("currentUser") == null ? 401 : 403).build();
        }
        try {
            return ResponseEntity.ok(cuponService.cambiarEstado(
                    java.util.UUID.fromString(id), activo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> eliminarCupon(@PathVariable String id, HttpSession session) {
        if (!esAdministrador(session)) {
            return ResponseEntity.status(session.getAttribute("currentUser") == null ? 401 : 403).build();
        }
        try {
            cuponService.eliminar(java.util.UUID.fromString(id));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private boolean esAdministrador(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        if (!(usuario instanceof Usuario usuarioActual)) {
            return false;
        }
        return "ADMINISTRADOR".equals(usuarioActual.getRol())
                || "SUPERADMIN".equals(usuarioActual.getRol());
    }
}
