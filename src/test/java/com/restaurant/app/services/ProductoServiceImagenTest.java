package com.restaurant.app.services;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductoService - reglas de imagen")
class ProductoServiceImagenTest {

    private ProductoRepository productoRepository;
    private ProductoSucursalRepository productoSucursalRepository;
    private com.restaurant.app.repository.SucursalRepository sucursalRepository;
    private SkuService skuService;
    private ProductoService productoService;

    @NonNull
    private static final UUID RESTAURANTE = TestIds.RESTAURANTE_A;

    @NonNull
    private static final UUID PRODUCTO_ID = TestIds.PRODUCTO_A;
    private static final String URL_HTTPS = "https://res.cloudinary.com/demo/image/upload/nexdine/productos/abc.jpg";

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        productoSucursalRepository = mock(ProductoSucursalRepository.class);
        sucursalRepository = mock(com.restaurant.app.repository.SucursalRepository.class);
        skuService = mock(SkuService.class);

        when(skuService.generarSku(RESTAURANTE)).thenReturn("EB-R007-000001");

        when(productoRepository.existsByRestauranteIdAndNombreNormalizado(any(), any()))
                .thenReturn(false);
        when(productoRepository.existsByRestauranteIdAndNombreNormalizadoAndIdNot(any(), any(), any()))
                .thenReturn(false);

        com.restaurant.app.model.Sucursal sucursal = new com.restaurant.app.model.Sucursal();
        sucursal.setId(TestIds.SUCURSAL_A1);
        sucursal.setNombre("Sucursal Centro");
        sucursal.setRestauranteId(RESTAURANTE);
        sucursal.setActiva(true);
        when(sucursalRepository.findById(TestIds.SUCURSAL_A1)).thenReturn(Optional.of(sucursal));

        productoService = new ProductoService(
                productoRepository,
                productoSucursalRepository,
                mock(com.restaurant.app.repository.CategoriaRepository.class),
                sucursalRepository,
                skuService);
    }

    Producto productoExistente(String imagen, String imagenPublicId) {
        Producto p = new Producto();
        p.setId(TestIds.PRODUCTO_A);
        p.setNombre("Bandeja Paisa");
        p.setDescripcion("Plato típico");
        p.setPrecio(new BigDecimal("28900"));
        p.setImagen(imagen);
        p.setImagenPublicId(imagenPublicId);
        p.setDisponible(true);
        p.setRestauranteId(RESTAURANTE);
        p.setSku("EB-R007-000001");
        p.setNombreNormalizado("bandeja paisa");
        return p;
    }

    @Test
    @DisplayName("CREAR guarda la URL HTTPS y el public_id de Cloudinary")
    void crearGuardaUrlHttpsYPublicId() {
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(TestIds.nuevo());
            return p;
        });

        Producto creado = productoService.createProducto(
                "Bandeja Paisa", "Plato típico", new BigDecimal("28900"),
                URL_HTTPS, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertEquals(URL_HTTPS, creado.getImagen());
        assertEquals(new BigDecimal("28900"), creado.getPrecio());
        assertEquals(RESTAURANTE, creado.getRestauranteId());
        assertTrue(creado.isDisponible());
        assertEquals("EB-R007-000001", creado.getSku());
        assertEquals("bandeja paisa", creado.getNombreNormalizado());
        assertNull(creado.getImagenPublicId());
    }

    @Test
    @DisplayName("CREAR sin imagen: producto válido (producto antiguo)")
    void crearSinImagen() {
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(TestIds.nuevo());
            return p;
        });

        Producto creado = productoService.createProducto(
                "Jugo", null, new BigDecimal("5000"),
                null, RESTAURANTE, List.of(TestIds.SUCURSAL_A1));

        assertNull(creado.getImagen());
        assertNull(creado.getImagenPublicId());
        assertEquals("EB-R007-000001", creado.getSku());
    }

    @Test
    @DisplayName("EDITAR SIN imagen (null) CONSERVA la imagen actual y su public_id")
    void editarSinImagenConservaLaActual() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Bandeja Paisa", "Nueva descripción", new BigDecimal("30000"),
                null, null, null, RESTAURANTE, null);

        assertEquals(URL_HTTPS, resultado.getImagen());
        assertEquals("nexdine/productos/abc", resultado.getImagenPublicId());
        assertEquals(new BigDecimal("30000"), resultado.getPrecio());
        verify(productoSucursalRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("EDITAR CON imagen nueva reemplaza URL y public_id")
    void editarConImagenNueva() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String nuevaUrl = "https://res.cloudinary.com/demo/image/upload/nexdine/productos/xyz.png";
        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Bandeja Paisa", null, new BigDecimal("28900"),
                nuevaUrl, "nexdine/productos/xyz", null, RESTAURANTE, null);

        assertEquals(nuevaUrl, resultado.getImagen());
        assertEquals("nexdine/productos/xyz", resultado.getImagenPublicId());
        assertEquals("EB-R007-000001", resultado.getSku());
        verify(productoRepository, times(1)).saveAndFlush(any(Producto.class));
    }

    @Test
    @DisplayName("EDITAR con imagen vacía la quita y limpia el public_id")
    void editarConImagenVacia() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Bandeja Paisa", null, new BigDecimal("28900"),
                "", null, null, RESTAURANTE, null);

        assertNull(resultado.getImagen());
        assertNull(resultado.getImagenPublicId());
    }

    @Test
    @DisplayName("EDITAR un producto de otro restaurante se rechaza")
    void editarProductoAjenoSeRechaza() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        actual.setRestauranteId(TestIds.RESTAURANTE_B);
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

        assertThrows(IllegalStateException.class, () -> productoService.updateProducto(
                PRODUCTO_ID, "X", null, new BigDecimal("1"),
                null, null, null, RESTAURANTE, null));
    }

    @Test
    @DisplayName("asignarImagen devuelve el public_id ANTERIOR para limpiar después")
    void asignarImagenDevuelvePublicIdAnterior() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        String anterior = productoService.asignarImagen(
                PRODUCTO_ID, "https://res.cloudinary.com/demo/image/upload/nexdine/productos/nuevo.jpg",
                "nexdine/productos/nuevo");

        assertEquals("nexdine/productos/abc", anterior);
        assertEquals("nexdine/productos/nuevo", actual.getImagenPublicId());
    }

    @Test
    @DisplayName("eliminarProducto devuelve el public_id previo sin fallar")
    void eliminarProductoDevuelvePublicId() {
        Producto actual = productoExistente(URL_HTTPS, "nexdine/productos/abc");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));
        when(productoSucursalRepository.findByProductoId(PRODUCTO_ID))
                .thenReturn(List.of(new ProductoSucursal()));

        String publicId = productoService.eliminarProductoConImagen(PRODUCTO_ID);

        assertEquals("nexdine/productos/abc", publicId);
        verify(productoRepository).deleteById(PRODUCTO_ID);
        verify(productoSucursalRepository).deleteAll(any());
    }

    @Test
    @DisplayName("imagenPublicIdSinOtrosUsos: no se borra si otro producto lo referencia")
    void imagenPublicIdCompartidaNoSeElimina() {
        when(productoRepository.countByImagenPublicIdAndIdNot("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(1L);

        assertEquals(false, productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID));
    }

    @Test
    @DisplayName("imagenPublicIdSinOtrosUsos: sí se borra si nadie más lo usa")
    void imagenPublicIdSinOtrosUsosSeElimina() {
        when(productoRepository.countByImagenPublicIdAndIdNot("nexdine/productos/abc", PRODUCTO_ID))
                .thenReturn(0L);

        assertTrue(productoService.imagenPublicIdSinOtrosUsos("nexdine/productos/abc", PRODUCTO_ID));
    }

    @Test
    @DisplayName("imagenPublicIdSinOtrosUsos: null significa que no hay nada que borrar")
    void imagenPublicIdNull() {
        assertTrue(productoService.imagenPublicIdSinOtrosUsos(null, PRODUCTO_ID));
        assertTrue(productoService.imagenPublicIdSinOtrosUsos("", PRODUCTO_ID));
    }
}
