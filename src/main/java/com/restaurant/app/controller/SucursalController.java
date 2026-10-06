package com.restaurant.app.controller;

import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.SucursalService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestión de sucursales — solo SUPERADMIN.
 * El restaurante se obtiene del contexto; nunca se recibe desde el frontend.
 */
@RestController
@RequestMapping("/sucursales")
public class SucursalController {

    @Autowired
    private SucursalService sucursalService;

    private ResponseEntity<?> requiereSuperadmin(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede administrar las sucursales."));
        }

        return null;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            List<Sucursal> sucursales = sucursalService.listar(usuarioActual);
            return ResponseEntity.ok(sucursales);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestParam String nombre,
                                   @RequestParam(required = false) String direccion,
                                   @RequestParam(required = false) String telefono,
                                   HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            return ResponseEntity.ok(sucursalService.crear(usuarioActual, nombre, direccion, telefono));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @RequestParam String nombre,
                                        @RequestParam(required = false) String direccion,
                                        @RequestParam(required = false) String telefono,
                                        HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            return ResponseEntity.ok(sucursalService.actualizar(usuarioActual, id, nombre, direccion, telefono));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id,
                                          @RequestParam boolean activa,
                                          HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            return ResponseEntity.ok(sucursalService.cambiarEstado(usuarioActual, id, activa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            sucursalService.eliminar(usuarioActual, id);
            return ResponseEntity.ok(Map.of("message", "Sucursal eliminada correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Relaciones activas: mensaje explicativo, no error genérico
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}