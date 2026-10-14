package com.restaurant.app.isolation;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.ProductoService;
import com.restaurant.app.services.SkuService;
import org.springframework.lang.NonNull;

import com.restaurant.app.util.TestIds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Aislamiento multi-restaurante entre tenants")
class AislamientoMultiRestauranteTest {

    private ProductoRepository productoRepository;
    private ProductoSucursalRepository productoSucursalRepository;
    private SucursalRepository sucursalRepository;
    private SkuService skuService;
    private ProductoService productoService;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        productoSucursalRepository = mock(ProductoSucursalRepository.class);
        sucursalRepository = mock(SucursalRepository.class);
        skuService = mock(SkuService.class);

        when(skuService.generarSku(any())).thenReturn("EB-R001-000001");

        when(sucursalRepository.findById(TestIds.SUCURSAL_A1))
                .thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_A1, TestIds.RESTAURANTE_A)));
        when(sucursalRepository.findById(TestIds.SUCURSAL_A2))
                .thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_A2, TestIds.RESTAURANTE_A)));
        when(sucursalRepository.findById(TestIds.SUCURSAL_B1))
                .thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_B1, TestIds.RESTAURANTE_B)));

        when(productoRepository.existsByRestauranteIdAndNombreNormalizado(any(), any()))
                .thenReturn(false);
        when(productoRepository.existsByRestauranteIdAndNombreNormalizadoAndIdNot(any(), any(), any()))
                .thenReturn(false);

        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(TestIds.nuevo());
            }
            return p;
        });

        productoService = new ProductoService(
                productoRepository,
                productoSucursalRepository,
                mock(CategoriaRepository.class),
                sucursalRepository,
                skuService);
    }

    private Sucursal sucursal(UUID id, UUID restauranteId) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre("Sucursal");
        s.setRestauranteId(restauranteId);
        s.setActiva(true);
        return s;
    }

    private Producto productoDe(UUID restauranteId, String nombre) {
        Producto p = new Producto();
        p.setId(TestIds.nuevo());
        p.setNombre(nombre);
        p.setPrecio(new BigDecimal("10000"));
        p.setRestauranteId(restauranteId);
        p.setDisponible(true);
        return p;
    }

    @Nested
    @DisplayName("Alta de productos")
    class AltaDeProductos {

        @Test
        @DisplayName("No se puede asociar un producto a una sucursal de otro restaurante")
        void noAsociaProductoASucursalAjena() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> productoService.createProducto(
                            "Combo", null, new BigDecimal("100"),
                            null, TestIds.RESTAURANTE_A, List.of(TestIds.SUCURSAL_B1)));

            assertTrue(ex.getMessage().contains("no pertenece a su restaurante"));

            verify(productoRepository, never()).saveAndFlush(any(Producto.class));
        }

        @Test
        @DisplayName("La relación creada lleva el tenant del producto")
        void relacionHeredaElTenant() {
            productoService.createProducto(
                    "Combo", null, new BigDecimal("100"),
                    null, TestIds.RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1, TestIds.SUCURSAL_A2));

            ArgumentCaptor<List<ProductoSucursal>> captor = ArgumentCaptor.captor();

            verify(productoSucursalRepository).saveAll(captor.capture());

            List<ProductoSucursal> guardadas = captor.getValue();
            assertEquals(2, guardadas.size());
            for (ProductoSucursal ps : guardadas) {
                assertEquals(TestIds.RESTAURANTE_A, ps.getRestauranteId(),
                        "Cada relacion debe llevar el restaurante del producto");
            }
        }

        @Test
        @DisplayName("Dos restaurantes pueden tener el mismo nombre de producto")
        void mismoNombreEnRestaurantesDistintos() {
            Producto a = productoService.createProducto(
                    "Hamburguesa", null, new BigDecimal("100"),
                    null, TestIds.RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

            Producto b = productoService.createProducto(
                    "Hamburguesa", null, new BigDecimal("100"),
                    null, TestIds.RESTAURANTE_B, List.of(TestIds.SUCURSAL_B1));

            assertNotNull(a.getId());
            assertNotNull(b.getId());
            assertTrue(!a.getId().equals(b.getId()));
        }
    }

    @Nested
    @DisplayName("Edición de productos")
    class EdicionDeProductos {

        @Test
        @DisplayName("No se puede editar un producto de otro restaurante")
        void noEditaProductoAjeno() {
            Producto ajeno = productoDe(TestIds.RESTAURANTE_B, "Hamburguesa");
            when(productoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

            assertThrows(IllegalStateException.class,
                    () -> productoService.updateProducto(
                            ajeno.getId(), "Hamburguesa", null, new BigDecimal("1"),
                            null, null, null, TestIds.RESTAURANTE_A, null));

            verify(productoRepository, never()).saveAndFlush(any(Producto.class));
        }

        @Test
        @DisplayName("No se puede mover un producto propio a una sucursal ajena")
        void noMueveProductoASucursalAjena() {
            Producto propio = productoDe(TestIds.RESTAURANTE_A, "Hamburguesa");
            when(productoRepository.findById(propio.getId())).thenReturn(Optional.of(propio));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> productoService.updateProducto(
                            propio.getId(), "Hamburguesa", null, new BigDecimal("1"),
                            null, null, null, TestIds.RESTAURANTE_A,
                            List.of(TestIds.SUCURSAL_A1, TestIds.SUCURSAL_B1)));

            assertTrue(ex.getMessage().contains("no pertenece a su restaurante"));
            verify(productoSucursalRepository, never()).save(any(ProductoSucursal.class));
        }
    }

    @Nested
    @DisplayName("Integridad en la capa de datos")
    class IntegridadDeDatos {

        @Test
        @DisplayName("Una violación de clave foránea se traduce a error, no a dato corrupto")
        void violacionDeIntegridadNoSeSilencia() {
            when(productoRepository.saveAndFlush(any(Producto.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "violates foreign key constraint "
                                    + "fk_producto_sucursal_producto_restaurante"));

            DataIntegrityViolationException ex = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> productoService.createProducto(
                            "Combo", null, new BigDecimal("100"),
                            null, TestIds.RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1)));

            assertTrue(ex.getMessage().contains("foreign key"));
        }

        @Test
        @DisplayName("El tenant se conserva en el producto guardado")
        void tenantSeConservaEnElProducto() {
            Producto creado = productoService.createProducto(
                    "Combo", null, new BigDecimal("100"),
                    null, TestIds.RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

            ArgumentCaptorVerificador.capturar(productoRepository, creado);

            assertEquals(TestIds.RESTAURANTE_A, creado.getRestauranteId());
        }
    }

    @Nested
    @DisplayName("Categorías")
    class Categorias {

        @Test
        @DisplayName("No se puede asignar al producto una categoría de otro restaurante")
        void noAsignaCategoriaAjena() {
            Producto propio = productoDe(TestIds.RESTAURANTE_A, "Hamburguesa");
            when(productoRepository.findById(propio.getId())).thenReturn(Optional.of(propio));

            Categoria categoriaAjena = new Categoria();
            categoriaAjena.setId(TestIds.CATEGORIA_A);
            categoriaAjena.setNombre("Entrantes");
            categoriaAjena.setRestauranteId(TestIds.RESTAURANTE_B);

            CategoriaRepository categorias = mock(CategoriaRepository.class);
            when(categorias.findById(TestIds.CATEGORIA_A)).thenReturn(Optional.of(categoriaAjena));

            ProductoService servicio = new ProductoService(
                    productoRepository, productoSucursalRepository, categorias,
                    sucursalRepository, skuService);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.updateProducto(
                            propio.getId(), "Hamburguesa", null, new BigDecimal("1"),
                            null, null, TestIds.CATEGORIA_A, TestIds.RESTAURANTE_A, null));

            assertTrue(ex.getMessage().contains("no pertenece a su restaurante"));
        }
    }

    static final class ArgumentCaptorVerificador {
        static void capturar(ProductoRepository repo, @NonNull Producto producto) {
            verify(repo, times(1)).saveAndFlush(producto);
        }
    }
}
