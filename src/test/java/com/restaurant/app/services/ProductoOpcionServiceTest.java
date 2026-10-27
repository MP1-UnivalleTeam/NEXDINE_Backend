package com.restaurant.app.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.app.model.ProductoOpcion;
import com.restaurant.app.model.ProductoOpcionGrupo;
import com.restaurant.app.repository.ProductoOpcionGrupoRepository;
import com.restaurant.app.repository.ProductoOpcionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ProductoOpcionService - RF020")
class ProductoOpcionServiceTest {

    private ProductoOpcionGrupoRepository grupos;
    private ProductoOpcionRepository opciones;
    private ProductoOpcionService service;

    private final UUID productoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        grupos = mock(ProductoOpcionGrupoRepository.class);
        opciones = mock(ProductoOpcionRepository.class);
        service = new ProductoOpcionService(grupos, opciones, new ObjectMapper());
    }

    private ProductoOpcionGrupo grupo(String nombre, boolean multiple, boolean obligatorio) {
        ProductoOpcionGrupo g = new ProductoOpcionGrupo();
        g.setId(UUID.randomUUID());
        g.setProductoId(productoId);
        g.setNombre(nombre);
        g.setPermiteMultiple(multiple);
        g.setObligatorio(obligatorio);
        return g;
    }

    private ProductoOpcion opcion(ProductoOpcionGrupo g, String nombre, String precio) {
        ProductoOpcion o = new ProductoOpcion();
        o.setId(UUID.randomUUID());
        o.setGrupoId(g.getId());
        o.setNombre(nombre);
        o.setPrecioAdicional(new BigDecimal(precio));
        o.setDisponible(true);
        return o;
    }

    @Test
    @DisplayName("obtenerOpciones expone el contrato: grupos, multiplicidad y precio de cada opción")
    void contratoOpciones() {
        ProductoOpcionGrupo g = grupo("Extras", true, false);
        ProductoOpcion o = opcion(g, "Queso", "2.50");

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId()))).thenReturn(List.of(o));

        List<java.util.Map<String, Object>> respuesta = service.obtenerOpciones(productoId);

        assertEquals(1, respuesta.size());
        assertEquals("Extras", respuesta.get(0).get("nombre"));
        assertEquals(Boolean.TRUE, respuesta.get(0).get("permiteMultiple"));
        assertEquals(Boolean.FALSE, respuesta.get(0).get("obligatorio"));

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> opciones = (List<java.util.Map<String, Object>>) respuesta.get(0).get("opciones");
        assertEquals("Queso", opciones.get(0).get("nombre"));
        assertEquals(0, new BigDecimal("2.50").compareTo((BigDecimal) opciones.get(0).get("precio")));
        assertEquals(Boolean.TRUE, opciones.get(0).get("disponible"));
    }

    @Test
    @DisplayName("validarSeleccion calcula el adicional desde los precios del backend")
    void adicionalEnServidor() {
        ProductoOpcionGrupo g = grupo("Extras", true, false);
        ProductoOpcion o1 = opcion(g, "Queso", "2.50");
        ProductoOpcion o2 = opcion(g, "Chimi", "1.00");

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId())))
                .thenReturn(List.of(o1, o2));

        var seleccion = service.validarSeleccion(
                productoId, List.of(o1.getId(), o2.getId()));

        assertEquals(0, new BigDecimal("3.50").compareTo(seleccion.adicional()));
        assertTrue(seleccion.nombres().contains("Queso"));
    }

    @Test
    @DisplayName("Grupo de selección única con dos opciones se rechaza")
    void seleccionUnicaViolada() {
        ProductoOpcionGrupo g = grupo("Tamaño", false, false);
        ProductoOpcion o1 = opcion(g, "Personal", "0");
        ProductoOpcion o2 = opcion(g, "Grande", "2.00");

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId())))
                .thenReturn(List.of(o1, o2));

        assertThrows(IllegalArgumentException.class,
                () -> service.validarSeleccion(productoId, List.of(o1.getId(), o2.getId())));
    }

    @Test
    @DisplayName("Opción que no pertenece al producto se rechaza")
    void opcionAjena() {
        ProductoOpcionGrupo g = grupo("Extras", true, false);
        ProductoOpcion o = opcion(g, "Ajena", "1.00");

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId())))
                .thenReturn(List.of(o));

        assertThrows(IllegalArgumentException.class,
                () -> service.validarSeleccion(productoId, List.of(UUID.randomUUID())));
    }

    @Test
    @DisplayName("Opción no disponible se rechaza")
    void opcionNoDisponible() {
        ProductoOpcionGrupo g = grupo("Extras", true, false);
        ProductoOpcion o = opcion(g, "Agotado", "1.00");
        o.setDisponible(false);

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId())))
                .thenReturn(List.of(o));

        assertThrows(IllegalArgumentException.class,
                () -> service.validarSeleccion(productoId, List.of(o.getId())));
    }

    @Test
    @DisplayName("Grupo obligatorio sin selección se rechaza")
    void obligatorioFaltante() {
        ProductoOpcionGrupo g = grupo("Base", false, true);
        ProductoOpcion o = opcion(g, "Tradicional", "0");

        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of(g));
        when(opciones.findByGrupoIdInOrderByOrdenAscNombreAsc(List.of(g.getId())))
                .thenReturn(List.of(o));

        assertThrows(IllegalArgumentException.class,
                () -> service.validarSeleccion(productoId, List.of()));
    }

    @Test
    @DisplayName("Producto sin configuración admite pedidos sin opciones")
    void productoSinOpciones() {
        when(grupos.findByProductoIdOrderByOrdenAscNombreAsc(productoId)).thenReturn(List.of());

        var seleccion = service.validarSeleccion(productoId, List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(seleccion.adicional()));
    }
}
