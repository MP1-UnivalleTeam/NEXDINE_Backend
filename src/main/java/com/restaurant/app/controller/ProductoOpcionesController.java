package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.repository.ProductoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/productos")
public class ProductoOpcionesController {

    private final ProductoRepository productoRepository;

    public ProductoOpcionesController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping("/{productoId}/opciones")
    public ResponseEntity<?> obtenerOpciones(@PathVariable UUID productoId) {
        Optional<Producto> producto = productoRepository.findById(productoId);
        if (producto.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Producto no encontrado"));
        }

        return ResponseEntity.ok(List.of());
    }
}
