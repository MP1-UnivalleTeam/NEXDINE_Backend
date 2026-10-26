package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.ProductoFavoritoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/clientes/favoritos")
public class ProductoFavoritoController {

    private final ProductoFavoritoService favoritoService;

    public ProductoFavoritoController(ProductoFavoritoService favoritoService) {
        this.favoritoService = favoritoService;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpSession session) {
        Usuario cliente = obtenerCliente(session);
        if (cliente == null) {
            return respuestaNoAutorizado(session);
        }
        return ResponseEntity.ok(favoritoService.listar(cliente.getId()));
    }

    @PostMapping("/{productoId}")
    public ResponseEntity<?> agregar(@PathVariable UUID productoId, HttpSession session) {
        Usuario cliente = obtenerCliente(session);
        if (cliente == null) {
            return respuestaNoAutorizado(session);
        }
        try {
            Producto producto = favoritoService.agregar(cliente.getId(), productoId);
            return ResponseEntity.ok(producto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{productoId}")
    public ResponseEntity<?> eliminar(@PathVariable UUID productoId, HttpSession session) {
        Usuario cliente = obtenerCliente(session);
        if (cliente == null) {
            return respuestaNoAutorizado(session);
        }
        favoritoService.eliminar(cliente.getId(), productoId);
        return ResponseEntity.noContent().build();
    }

    private Usuario obtenerCliente(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        if (usuario instanceof Usuario u && "CLIENTE".equals(u.getRol())) {
            return u;
        }
        return null;
    }

    private ResponseEntity<?> respuestaNoAutorizado(HttpSession session) {
        return session.getAttribute("currentUser") == null
                ? ResponseEntity.status(401).build()
                : ResponseEntity.status(403).body(Map.of("error", "Solo los clientes pueden gestionar favoritos."));
    }
}
