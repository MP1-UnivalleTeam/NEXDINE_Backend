package com.restaurant.app.controller;

import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.MesaService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/mesas")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    @GetMapping
    public ResponseEntity<?> listarMesas(@RequestParam(required = false) UUID sucursalId,
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
                                       @RequestParam(required = false) UUID sucursalId,
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
    public ResponseEntity<?> cambiarEstado(@PathVariable @NonNull UUID id,
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
