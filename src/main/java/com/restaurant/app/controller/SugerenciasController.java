package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.repository.ProductoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sugerencias")
public class SugerenciasController {

    private final ProductoRepository productoRepository;

    public SugerenciasController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping
    public ResponseEntity<?> obtenerSugerencias(@RequestParam UUID productoId) {
        Optional<Producto> productoActual = productoRepository.findById(productoId);
        if (productoActual.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Producto actual = productoActual.get();
        List<Producto> sugerencias = productoRepository.findAll().stream()
                .filter(p -> p.isDisponible())
                .filter(p -> !p.getId().equals(productoId))
                .filter(p -> mismoCategoria(p, actual))
                .limit(6)
                .collect(Collectors.toList());

        List<Map<String, Object>> response = sugerencias.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("descripcion", p.getDescripcion());
            item.put("precio", p.getPrecio());
            item.put("imagen", p.getImagen());
            item.put("disponible", p.isDisponible());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private boolean mismoCategoria(Producto a, Producto b) {
        if (a.getCategoriaId() == null || b.getCategoriaId() == null) {
            return false;
        }
        return a.getCategoriaId().equals(b.getCategoriaId());
    }
}
