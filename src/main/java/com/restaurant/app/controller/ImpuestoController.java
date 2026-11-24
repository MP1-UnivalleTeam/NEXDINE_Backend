package com.restaurant.app.controller;

import com.restaurant.app.dto.ConfiguracionImpuestoRequest;
import com.restaurant.app.dto.EstadoImpuestoRequest;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.ConfiguracionImpuestoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/impuestos")
public class ImpuestoController {

    private final ConfiguracionImpuestoService configuracionImpuestoService;

    public ImpuestoController(ConfiguracionImpuestoService configuracionImpuestoService) {
        this.configuracionImpuestoService = configuracionImpuestoService;
    }

    @GetMapping("/configuracion")
    public ResponseEntity<?> consultar(HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(configuracionImpuestoService.consultar(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/configuracion")
    public ResponseEntity<?> actualizar(@RequestBody ConfiguracionImpuestoRequest solicitud,
                                        HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(configuracionImpuestoService.actualizar(usuario, solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/configuracion/estado")
    public ResponseEntity<?> cambiarEstado(@RequestBody EstadoImpuestoRequest solicitud,
                                           HttpSession session) {
        Usuario usuario = usuarioActual(session);
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(configuracionImpuestoService.cambiarEstado(
                    usuario, solicitud == null ? null : solicitud.activo()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    private Usuario usuarioActual(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        return usuario instanceof Usuario u ? u : null;
    }
}
