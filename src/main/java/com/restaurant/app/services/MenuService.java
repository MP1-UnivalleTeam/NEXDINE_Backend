package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;

import org.springframework.stereotype.Service;

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

    /**
     * Menú de una sucursal concreta: solo productos con relación en
     * producto_sucursal para esa sucursal y con disponible = true.
     *
     * Refleja RF007 (disponibilidad por sucursal).
     */
    public Map<String, Object> getMenuPorSucursal(Long sucursalId) {
        List<Producto> todos = productoRepository.findAll();

        List<Producto> productos = todos.stream()
                .filter(Producto::isDisponible)
                .filter(p -> productoSucursalRepository
                        .findByProductoIdAndSucursalId(p.getId(), sucursalId)
                        .map(ProductoSucursal::isDisponible)
                        .orElse(false))
                .toList();

        return construirMenu(productos);
    }

    /**
     * Menú público de un restaurante, filtrado por su catálogo.
     * No requiere sesión ni mesa.
     */
    public Map<String, Object> getMenuPublicoPorRestaurante(Long restauranteId) {
        List<Producto> productos = productoRepository.findDisponiblesByRestaurante(restauranteId);
        return construirMenu(productos);
    }

    /**
     * Obtiene el menú disponible para el cliente.
     * Retorna elementos agrupados por categoría para que el frontend
     * pueda mostrar nombre, descripción, precio, imagen y disponibilidad.
     */
    public Map<String, Object> getMenuDisponible() {
        List<Producto> productos = productoRepository.findDisponibles();
        return construirMenu(productos);
    }

    private Map<String, Object> construirMenu(List<Producto> productos) {

        // Construir lista de elementos con info de categoría
        List<Map<String, Object>> items = new ArrayList<>();

        for (Producto p : productos) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("nombre", p.getNombre());
            item.put("descripcion", p.getDescripcion());
            item.put("precio", p.getPrecio());
            item.put("imagen", p.getImagen());

            // Resolver nombre de categoría
            String categoriaNombre = "Sin categoría";
            if (p.getCategoriaId() != null) {
                Categoria cat = categoriaRepository.findById(p.getCategoriaId()).orElse(null);
                if (cat != null) {
                    categoriaNombre = cat.getNombre();
                }
            }
            item.put("categoria", categoriaNombre);

            // Disponibilidad: general (producto) + operacional (producto_sucursal)
            boolean operacional = true;
            List<ProductoSucursal> psList = productoSucursalRepository.findByProductoId(p.getId());
            if (!psList.isEmpty()) {
                operacional = psList.stream().anyMatch(ProductoSucursal::isDisponible);
            }
            item.put("disponible", p.isDisponible() && operacional);

            items.add(item);
        }

        // Agrupar por categoría
        Map<String, List<Map<String, Object>>> porCategoria = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String cat = (String) item.get("categoria");
            porCategoria.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }

        // Construir respuesta
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
