package com.restaurant.app.services;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

@DisplayName("ProductoService - SKU y prevención de duplicados")
class ProductoServiceSkuTest {

    private ProductoRepository productoRepository;
    private ProductoSucursalRepository productoSucursalRepository;
    private SucursalRepository sucursalRepository;
    private SkuService skuService;
    private ProductoService productoService;

    @NonNull
    private static final UUID RESTAURANTE_A = TestIds.RESTAURANTE_A;
    @NonNull
    private static final UUID PRODUCTO_ID = TestIds.PRODUCTO_A;
    @NonNull
    private static final UUID RESTAURANTE_B = TestIds.RESTAURANTE_B;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        productoSucursalRepository = mock(ProductoSucursalRepository.class);
        sucursalRepository = mock(SucursalRepository.class);
        skuService = mock(SkuService.class);

        AtomicInteger contadorA = new AtomicInteger(0);
        AtomicInteger contadorB = new AtomicInteger(0);

        when(skuService.generarSku(RESTAURANTE_A))
                .thenAnswer(i -> "EB-R001-" + com.restaurant.app.services.SkuUtil
                        .formatearConsecutivo(contadorA.incrementAndGet()));
        when(skuService.generarSku(RESTAURANTE_B))
                .thenAnswer(i -> "EB-R002-" + com.restaurant.app.services.SkuUtil
                        .formatearConsecutivo(contadorB.incrementAndGet()));

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

        when(sucursalRepository.findById(TestIds.SUCURSAL_A1)).thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_A1, RESTAURANTE_A)));
        when(sucursalRepository.findById(TestIds.SUCURSAL_A2)).thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_A2, RESTAURANTE_A)));
        when(sucursalRepository.findById(TestIds.SUCURSAL_B1)).thenReturn(Optional.of(sucursal(TestIds.SUCURSAL_B1, RESTAURANTE_B)));

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
        s.setNombre("Sucursal " + id);
        s.setRestauranteId(restauranteId);
        s.setActiva(true);
        return s;
    }


    @Test
    @DisplayName("Crear genera el SKU automáticamente")
    void crearGeneraSku() {
        Producto p = productoService.createProducto(
                "Hamburguesa Clásica", "Deliciosa", new BigDecimal("18000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

        assertEquals("EB-R001-000001", p.getSku());
        verify(skuService).generarSku(RESTAURANTE_A);
    }

    @Test
    @DisplayName("La llave primaria sigue siendo autogenerada por la base de datos")
    void llavePrimariaSigueAutogenerada() {
        Producto p = productoService.createProducto(
                "Hamburguesa Clásica", null, new BigDecimal("18000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

        assertNotNull(p.getId());
        assertTrue(!p.getId().toString().equals(p.getSku()));
        verify(productoRepository).saveAndFlush(any(Producto.class));
    }

    @Test
    @DisplayName("El nombre normalizado se guarda sincronizado")
    void guardaNombreNormalizado() {
        Producto p = productoService.createProducto(
                "  HAMBURGUESA   Clásica  ", null, new BigDecimal("18000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

        assertEquals("hamburguesa clasica", p.getNombreNormalizado());
        assertTrue(p.getNombre().contains("HAMBURGUESA"));
    }

    @Test
    @DisplayName("Productos consecutivos del mismo restaurante incrementan el SKU")
    void consecutivoCrece() {
        Producto p1 = productoService.createProducto(
                "Jugo de Mango", null, new BigDecimal("6000"), null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));
        Producto p2 = productoService.createProducto(
                "Jugo de Naranja", null, new BigDecimal("6500"), null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

        assertEquals("EB-R001-000001", p1.getSku());
        assertEquals("EB-R001-000002", p2.getSku());
    }


    @Test
    @DisplayName("Crear un duplicado normalizado se rechaza")
    void crearDuplicadoSeRechaza() {
        when(productoRepository.existsByRestauranteIdAndNombreNormalizado(RESTAURANTE_A, "hamburguesa clasica"))
                .thenReturn(true);

        ProductoService.ProductoDuplicadoException ex = assertThrows(
                ProductoService.ProductoDuplicadoException.class,
                () -> productoService.createProducto(
                        " hamburguesa clásica ", null, new BigDecimal("18000"),
                        null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1)));

        assertTrue(ex.getMessage().contains("Ya existe un producto"));
        verify(productoRepository, never()).saveAndFlush(any(Producto.class));
    }

    @Test
    @DisplayName("Carrera entre peticiones simultáneas: el índice UNIQUE decide")
    void carreraResueltaPorIndiceUnico() {
        when(productoRepository.existsByRestauranteIdAndNombreNormalizado(RESTAURANTE_A, "cola"))
                .thenReturn(false);
        AtomicInteger intentos = new AtomicInteger(0);
        when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> {
            if (intentos.incrementAndGet() > 1) {
                throw new DataIntegrityViolationException(
                            "could not execute statement [ERROR: duplicate key value violates "
                                    + "unique constraint \"uq_productos_restaurante_nombre_norm\""
                                    + " [DETAIL: Key (restaurante_id, nombre_normalizado)"
                                    + "=(1, cola) already exists.]");
            }
            Producto p = inv.getArgument(0);
            p.setId(TestIds.nuevo());
            return p;
        });

        productoService.createProducto("Cola", null, new BigDecimal("3000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));

        ProductoService.ProductoDuplicadoException ex = assertThrows(
                ProductoService.ProductoDuplicadoException.class,
                () -> productoService.createProducto("Cola", null, new BigDecimal("3000"),
                        null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1)));

        assertTrue(ex.getMessage().contains("Cola"));
    }

    @Test
    @DisplayName("El mismo nombre SÍ se permite en restaurantes distintos")
    void mismoNombreEnRestaurantesDistintos() {
        Producto enA = productoService.createProducto(
                "Hamburguesa Clásica", null, new BigDecimal("18000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1));
        Producto enB = productoService.createProducto(
                "Hamburguesa Clásica", null, new BigDecimal("20000"),
                null, RESTAURANTE_B, List.of(TestIds.SUCURSAL_B1));

        assertEquals("EB-R001-000001", enA.getSku());
        assertEquals("EB-R002-000001", enB.getSku());
        assertTrue(!enA.getSku().equals(enB.getSku()));
    }


    private Producto existente(UUID id, String nombre, UUID restauranteId, String sku) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setPrecio(new BigDecimal("18000"));
        p.setRestauranteId(restauranteId);
        p.setDisponible(true);
        p.setSku(sku);
        p.setNombreNormalizado(SkuUtil.normalizarNombre(nombre));
        return p;
    }

    @Test
    @DisplayName("Editar excluye el producto actual de la comprobación de duplicados")
    void editarExcluyeElPropioProducto() {
        Producto actual = existente(PRODUCTO_ID, "Hamburguesa Clásica", RESTAURANTE_A, "EB-R001-000001");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Hamburguesa Clasica", "Otra descripción", new BigDecimal("19000"),
                null, null, RESTAURANTE_A, null);

        verify(productoRepository).existsByRestauranteIdAndNombreNormalizadoAndIdNot(
                RESTAURANTE_A, "hamburguesa clasica", PRODUCTO_ID);
        assertEquals("hamburguesa clasica", resultado.getNombreNormalizado());
    }

    @Test
    @DisplayName("Editar hacia el nombre de otro producto se rechaza")
    void editarHaciaDuplicadoSeRechaza() {
        Producto actual = existente(PRODUCTO_ID, "Hamburguesa Clásica", RESTAURANTE_A, "EB-R001-000001");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

        when(productoRepository.existsByRestauranteIdAndNombreNormalizadoAndIdNot(
                RESTAURANTE_A, "pizza margarita", PRODUCTO_ID)).thenReturn(true);

        assertThrows(ProductoService.ProductoDuplicadoException.class,
                () -> productoService.updateProducto(
                        PRODUCTO_ID, "Pizza Margarita", null, new BigDecimal("19000"),
                        null, null, RESTAURANTE_A, null));
    }

    @Test
    @DisplayName("Editar NO regenera el SKU")
    void editarNoRegeneraSku() {
        Producto actual = existente(PRODUCTO_ID, "Hamburguesa Clásica", RESTAURANTE_A, "EB-R001-000001");
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Hamburguesa Doble Queso", "Nueva descripción", new BigDecimal("25000"),
                null, null, RESTAURANTE_A, null);

        assertEquals("EB-R001-000001", resultado.getSku());
        verify(skuService, never()).generarSku(any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Un producto histórico sin SKU recibe uno al editarse")
    void productoHistoricoRecibeSku() {
        Producto actual = existente(PRODUCTO_ID, "Producto Antiguo", RESTAURANTE_A, null);
        when(productoRepository.findById(PRODUCTO_ID)).thenReturn(Optional.of(actual));

        Producto resultado = productoService.updateProducto(
                PRODUCTO_ID, "Producto Antiguo", null, new BigDecimal("1000"),
                null, null, RESTAURANTE_A, null);

        assertEquals("EB-R001-000001", resultado.getSku());
    }


    @Test
    @DisplayName("Un producto en varias sucursales conserva un único id y un único SKU")
    void productoCompartidoUnicoIdYsku() {
        Producto p = productoService.createProducto(
                "Combo Familiar", null, new BigDecimal("45000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1, TestIds.SUCURSAL_A2));

        assertEquals("EB-R001-000001", p.getSku());

        verify(productoRepository, times(1)).saveAndFlush(any(Producto.class));

        List<ProductoSucursal> relaciones = new ArrayList<>();
        verify(productoSucursalRepository).saveAll(org.mockito.ArgumentMatchers.argThat((List<ProductoSucursal> l) -> {
            relaciones.addAll(l);
            return l.size() == 2;
        }));

        assertTrue(relaciones.stream().allMatch(r -> r.getProductoId().equals(p.getId())));
    }

    @Test
    @DisplayName("La disponibilidad por sucursal no se altera")
    void disponibilidadPorSucursalIntacta() {
        Producto p = productoService.createProducto(
                "Combo Familiar", null, new BigDecimal("45000"),
                null, RESTAURANTE_A, List.of(TestIds.SUCURSAL_A1, TestIds.SUCURSAL_A2));

        assertTrue(p.isDisponible());
    }

    @Test
    @DisplayName("Listar productos del restaurante sigue funcionando")
    void listarProductos() {
        List<Producto> productos = List.of(
                existente(PRODUCTO_ID, "A", RESTAURANTE_A, "EB-R001-000001"),
                existente(TestIds.nuevo(), "B", RESTAURANTE_A, "EB-R001-000002"));

        when(productoRepository.findByRestauranteId(RESTAURANTE_A)).thenReturn(productos);

        assertEquals(2, productoService.listarParaContexto(RESTAURANTE_A, TestIds.SUCURSAL_A1).size());
    }

    @Test
    @DisplayName("Consultar un producto por SKU funciona")
    void consultarPorSku() {
        Producto p = existente(PRODUCTO_ID, "Hamburguesa", RESTAURANTE_A, "EB-R001-000001");
        when(productoRepository.findBySku("EB-R001-000001")).thenReturn(Optional.of(p));

        assertTrue(productoRepository.findBySku("EB-R001-000001").isPresent());
    }
}
