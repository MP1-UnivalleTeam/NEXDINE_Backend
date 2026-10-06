package com.restaurant.app.controller;

import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.MesaService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mesas")
public class MesaController {

    @Autowired
    private MesaService mesaService;

    @GetMapping
    public ResponseEntity<?> listarMesas(@RequestParam(required = false) Long sucursalId,
                                         HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            List<Mesa> mesas = mesaService.listarMesas(usuarioActual, sucursalId);
            return ResponseEntity.ok(mesas);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al listar las mesas: " + e.getMessage()));
        }
    }

    @GetMapping("/sucursales")
    public ResponseEntity<?> listarSucursales(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            List<Sucursal> sucursales = mesaService.listarSucursalesPermitidas(usuarioActual);
            return ResponseEntity.ok(sucursales);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al listar las sucursales: " + e.getMessage()));
        }
    }

    /**
     * Enlace público del menú del restaurante (RF002).
     * Devuelve nombre y slug para que el SUPERADMIN comparta el enlace.
     * El slug se genera automáticamente si el restaurante aún no lo tiene.
     */
    @GetMapping("/enlace-publico")
    public ResponseEntity<?> enlacePublico(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            Restaurante restaurante = mesaService.obtenerRestauranteConSlug(usuarioActual);
            return ResponseEntity.ok(Map.of(
                    "restaurante", restaurante.getNombre(),
                    "slug", restaurante.getSlug()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al obtener el enlace público: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crearMesa(@RequestParam String nombre,
                                       @RequestParam(required = false) Long sucursalId,
                                       HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            Mesa mesa = mesaService.crearMesa(usuarioActual, sucursalId, nombre);
            return ResponseEntity.ok(mesa);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al crear la mesa: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id,
                                           @RequestParam boolean activa,
                                           HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            Mesa mesa = mesaService.cambiarEstadoMesa(usuarioActual, id, activa);
            return ResponseEntity.ok(mesa);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al cambiar el estado de la mesa: " + e.getMessage()));
        }
    }
}