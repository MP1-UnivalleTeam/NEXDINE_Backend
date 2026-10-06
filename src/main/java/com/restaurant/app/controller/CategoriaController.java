package com.restaurant.app.controller;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CategoriaService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gestión de categorías del restaurante — solo SUPERADMIN.
 */
@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    private ResponseEntity<?> requiereSuperadmin(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede administrar las categorías."));
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
            return ResponseEntity.ok(categoriaService.listar(usuarioActual));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestParam String nombre,
                                   @RequestParam(required = false) String descripcion,
                                   HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            return ResponseEntity.ok(categoriaService.crear(usuarioActual, nombre, descripcion));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @RequestParam String nombre,
                                        @RequestParam(required = false) String descripcion,
                                        @RequestParam(required = false) Boolean activa,
                                        HttpSession session) {
        ResponseEntity<?> unauthorized = requiereSuperadmin(session);
        if (unauthorized != null) {
            return unauthorized;
        }

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        try {
            return ResponseEntity.ok(
                    categoriaService.actualizar(usuarioActual, id, nombre, descripcion, activa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}