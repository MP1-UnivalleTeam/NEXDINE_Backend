package com.restaurant.app.controller;

import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.services.ProductoOpcionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/productos")
public class ProductoOpcionesController {

    private final ProductoRepository productoRepository;
    private final ProductoOpcionService productoOpcionService;

    public ProductoOpcionesController(ProductoRepository productoRepository,
                                      ProductoOpcionService productoOpcionService) {
        this.productoRepository = productoRepository;
        this.productoOpcionService = productoOpcionService;
    }

    @GetMapping("/{productoId}/opciones")
    public ResponseEntity<?> obtenerOpciones(@PathVariable UUID productoId) {
        if (!productoRepository.existsById(productoId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Producto no encontrado"));
        }
        return ResponseEntity.ok(productoOpcionService.obtenerOpciones(productoId));
    }
}
