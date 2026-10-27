package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.services.ProductoOpcionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sugerencias")
public class SugerenciasController {

    private final ProductoRepository productoRepository;
    private final ProductoSucursalRepository productoSucursalRepository;
    private final ProductoOpcionService productoOpcionService;

    public SugerenciasController(ProductoRepository productoRepository,
                                 ProductoSucursalRepository productoSucursalRepository,
                                 ProductoOpcionService productoOpcionService) {
        this.productoRepository = productoRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.productoOpcionService = productoOpcionService;
    }

    @GetMapping
    public ResponseEntity<?> obtenerSugerencias(@RequestParam UUID productoId,
                                                 @RequestParam(required = false) UUID sucursalId,
                                                 @RequestParam(required = false) List<UUID> excluir) {
        Producto actual = productoRepository.findById(productoId).orElse(null);
        if (actual == null) {
            return ResponseEntity.ok(List.of());
        }

        Set<UUID> exclusiones = excluir == null ? Set.of()
                : excluir.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<Producto> candidatos = actual.getCategoriaId() == null
                ? productoRepository.findByRestauranteIdAndDisponibleTrue(actual.getRestauranteId())
                : productoRepository.findByRestauranteIdAndCategoriaIdAndDisponibleTrue(
                        actual.getRestauranteId(), actual.getCategoriaId());

        List<Map<String, Object>> response = new ArrayList<>();
        for (Producto producto : candidatos) {
            if (producto.getId().equals(productoId)
                    || exclusiones.contains(producto.getId())
                    || !producto.isDisponible()) {
                continue;
            }
            if (sucursalId != null && !productoSucursalRepository
                    .existsByProductoIdAndSucursalIdAndDisponibleTrue(producto.getId(), sucursalId)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", producto.getId());
            item.put("nombre", producto.getNombre());
            item.put("descripcion", producto.getDescripcion());
            item.put("precio", producto.getPrecio());
            item.put("imagen", producto.getImagen());
            item.put("disponible", producto.isDisponible());
            item.put("categoriaId", producto.getCategoriaId());
            item.put("tieneOpciones", productoOpcionService.tieneOpciones(producto.getId()));
            response.add(item);
            if (response.size() >= 6) {
                break;
            }
        }

        return ResponseEntity.ok(response);
    }
}
