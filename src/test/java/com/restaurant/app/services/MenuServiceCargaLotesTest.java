package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MenuService - carga por lotes sin cambiar el contrato")
class MenuServiceCargaLotesTest {

    private ProductoRepository productoRepository;
    private CategoriaRepository categoriaRepository;
    private ProductoSucursalRepository productoSucursalRepository;
    private com.restaurant.app.repository.ProductoOpcionGrupoRepository productoOpcionGrupoRepository;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        productoSucursalRepository = mock(ProductoSucursalRepository.class);
        productoOpcionGrupoRepository =
                mock(com.restaurant.app.repository.ProductoOpcionGrupoRepository.class);

        menuService = new MenuService(productoRepository, categoriaRepository,
                productoSucursalRepository, productoOpcionGrupoRepository);
    }

    private Producto producto(long n, UUID categoriaId, boolean disponible) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre("Producto " + n);
        p.setDescripcion("Descripcion " + n);
        p.setPrecio(BigDecimal.valueOf(1000 * n));
        p.setImagen("https://res.cloudinary.com/demo/" + n + ".png");
        p.setCategoriaId(categoriaId);
        p.setDisponible(disponible);
        p.setRestauranteId(UUID.randomUUID());
        return p;
    }

    @Test
    @DisplayName("100 productos se resuelven con 3 consultas, no 201")
    void construccionUsaCargaPorLotes() {
        UUID restauranteId = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();

        List<Producto> productos = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            productos.add(producto(i, categoriaId, true));
        }

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(productos);

        Categoria categoria = new Categoria();
        categoria.setId(categoriaId);
        categoria.setNombre("Platos");
        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of(categoria));
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        verify(categoriaRepository, Mockito.times(1)).findByIdIn(any());
        verify(productoSucursalRepository, Mockito.times(1)).findByProductoIdIn(any());

        verify(categoriaRepository, Mockito.never()).findById(any());
        verify(productoSucursalRepository, Mockito.never()).findByProductoId(any(UUID.class));

        List<Map<String, Object>> items = itemsDe(respuesta);
        assertEquals(100, items.size());
    }

    @Test
    @DisplayName("El contrato de la respuesta no cambia")
    void contratoDeRespuestaIntacto() {
        UUID restauranteId = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();

        Producto p = producto(1, categoriaId, true);

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of(p));

        Categoria categoria = new Categoria();
        categoria.setId(categoriaId);
        categoria.setNombre("Entrantes");
        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of(categoria));
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertTrue(respuesta.containsKey("categorias"));

        List<Map<String, Object>> items = itemsDe(respuesta);
        assertEquals(1, items.size());

        Map<String, Object> item = items.get(0);
        assertEquals(p.getId(), item.get("id"));
        assertEquals(p.getNombre(), item.get("nombre"));
        assertEquals(p.getDescripcion(), item.get("descripcion"));
        assertEquals(p.getPrecio(), item.get("precio"));
        assertEquals(p.getImagen(), item.get("imagen"));
        assertEquals("Entrantes", item.get("categoria"));
        assertEquals(Boolean.TRUE, item.get("disponible"));
        assertEquals(Boolean.FALSE, item.get("tieneOpciones"));
    }

    @Test
    @DisplayName("Producto sin categoría sigue mostrando 'Sin categoría'")
    void productoSinCategoria() {
        UUID restauranteId = UUID.randomUUID();
        Producto p = producto(1, null, true);

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of(p));
        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of());
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertEquals("Sin categoría", itemsDe(respuesta).get(0).get("categoria"));
    }

    @Test
    @DisplayName("La disponibilidad es agregada: disponible si alguna sucursal lo tiene")
    void disponibilidadAgregada() {
        UUID restauranteId = UUID.randomUUID();
        Producto p = producto(1, null, true);
        p.setRestauranteId(restauranteId);

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of(p));

        List<ProductoSucursal> relaciones = new ArrayList<>();
        relaciones.add(new ProductoSucursal(p.getId(), UUID.randomUUID(), restauranteId));
        ProductoSucursal noDisponible = new ProductoSucursal(p.getId(), UUID.randomUUID(), restauranteId);
        noDisponible.setDisponible(false);
        relaciones.add(noDisponible);

        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of());
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(relaciones);

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertEquals(Boolean.TRUE, itemsDe(respuesta).get(0).get("disponible"));
    }

    @Test
    @DisplayName("Producto no disponible en ninguna sucursal queda no disponible")
    void disponibilidadEnNingunaSucursal() {
        UUID restauranteId = UUID.randomUUID();
        Producto p = producto(1, null, true);
        p.setRestauranteId(restauranteId);

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of(p));

        ProductoSucursal noDisponible = new ProductoSucursal(p.getId(), UUID.randomUUID(), restauranteId);
        noDisponible.setDisponible(false);

        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of());
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(List.of(noDisponible));

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertEquals(Boolean.FALSE, itemsDe(respuesta).get(0).get("disponible"));
    }

    @Test
    @DisplayName("Un producto sin relaciones sigue considerándose disponible")
    void productoSinRelaciones() {
        UUID restauranteId = UUID.randomUUID();
        Producto p = producto(1, null, true);
        p.setRestauranteId(restauranteId);

        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of(p));
        when(categoriaRepository.findByIdIn(any())).thenReturn(List.of());
        when(productoSucursalRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertEquals(Boolean.TRUE, itemsDe(respuesta).get(0).get("disponible"));
    }

    @Test
    @DisplayName("Menú vacío responde correctamente sin consultas adicionales")
    void menuVacio() {
        UUID restauranteId = UUID.randomUUID();
        when(productoRepository.findDisponiblesByRestaurante(restauranteId)).thenReturn(List.of());

        Map<String, Object> respuesta = menuService.getMenuPublicoPorRestaurante(restauranteId);

        assertTrue(itemsDe(respuesta).isEmpty());
        verify(categoriaRepository, Mockito.never()).findByIdIn(any());
        verify(productoSucursalRepository, Mockito.never()).findByProductoIdIn(any());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsDe(Map<String, Object> respuesta) {
        List<Map<String, Object>> todos = new ArrayList<>();
        for (Map<String, Object> categoria : (List<Map<String, Object>>) respuesta.get("categorias")) {
            todos.addAll((List<Map<String, Object>>) categoria.get("productos"));
        }
        return todos;
    }
}
