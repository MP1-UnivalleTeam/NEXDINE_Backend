package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.*;

@Service
public class MenuService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoSucursalRepository productoSucursalRepository;

    public MenuService(ProductoRepository productoRepository,
                       CategoriaRepository categoriaRepository,
                       ProductoSucursalRepository productoSucursalRepository) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoSucursalRepository = productoSucursalRepository;
    }

    private static final String SIN_CATEGORIA = "Sin categoría";

    private Map<UUID, String> cargarNombresCategoria(List<Producto> productos) {
        List<UUID> ids = productos.stream()
                .map(producto -> producto.getCategoriaId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        Map<UUID, String> nombres = new HashMap<>();
        for (Categoria categoria : categoriaRepository.findByIdIn(ids)) {
            nombres.put(categoria.getId(), categoria.getNombre());
        }
        return nombres;
    }

    private Map<UUID, Boolean> cargarDisponibilidadOperacional(List<Producto> productos) {
        List<UUID> ids = productos.stream()
                .map(producto -> producto.getId())
                .filter(java.util.Objects::nonNull)
                .toList();

        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        Map<UUID, Boolean> resultado = new HashMap<>();
        for (ProductoSucursal ps : productoSucursalRepository.findByProductoIdIn(ids)) {
            resultado.merge(ps.getProductoId(), ps.isDisponible(),
                    (existente, nuevo) -> existente || nuevo);
        }
        return resultado;
    }

    public Map<String, Object> getMenuPorSucursal(UUID sucursalId) {
        List<Producto> todos = productoRepository.findAll();

        List<Producto> productos = todos.stream()
                .filter(producto -> producto.isDisponible())
                .filter(p -> productoSucursalRepository
                        .findByProductoIdAndSucursalId(p.getId(), sucursalId)
                        .map(relacion -> relacion.isDisponible())
                        .orElse(false))
                .toList();

        return construirMenu(productos);
    }

    public Map<String, Object> getMenuPublicoPorRestaurante(UUID restauranteId) {
        List<Producto> productos = productoRepository.findDisponiblesByRestaurante(restauranteId);
        return construirMenu(productos);
    }

    public Map<String, Object> getMenuDisponible() {
        List<Producto> productos = productoRepository.findDisponibles();
        return construirMenu(productos);
    }

    private Map<String, Object> construirMenu(List<Producto> productos) {

        if (productos.isEmpty()) {
            return Map.of("categorias", List.of());
        }

        Map<UUID, String> nombresCategoria = cargarNombresCategoria(productos);
        Map<UUID, Boolean> disponibilidadOperacional = cargarDisponibilidadOperacional(productos);

        List<Map<String, Object>> items = new ArrayList<>();

        for (Producto p : productos) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("descripcion", p.getDescripcion());
            item.put("precio", p.getPrecio());
            item.put("imagen", p.getImagen());
            String categoria = (p.getCategoriaId() == null)
                    ? SIN_CATEGORIA
                    : nombresCategoria.getOrDefault(p.getCategoriaId(), SIN_CATEGORIA);
            item.put("categoria", categoria);

            boolean operacional = disponibilidadOperacional.getOrDefault(p.getId(), Boolean.TRUE);
            item.put("disponible", p.isDisponible() && operacional);

            items.add(item);
        }

        Map<String, List<Map<String, Object>>> porCategoria = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String cat = (String) item.get("categoria");
            porCategoria.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> categoriasResponse = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : porCategoria.entrySet()) {
            Map<String, Object> catObj = new HashMap<>();
            catObj.put("nombre", entry.getKey());
            catObj.put("productos", entry.getValue());
            categoriasResponse.add(catObj);
        }

        response.put("categorias", categoriasResponse);
        return response;
    }
}
