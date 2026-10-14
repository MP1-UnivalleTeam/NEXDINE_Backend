package com.restaurant.app.services;

import com.restaurant.app.util.TestIds;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.repository.RestauranteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SkuService - generación de SKU")
class SkuServiceTest {

    private RestauranteRepository restauranteRepository;
    private SkuService skuService;

    @NonNull
    private static final UUID RESTAURANTE_1 = TestIds.RESTAURANTE_A;
    @NonNull
    private static final UUID RESTAURANTE_2 = TestIds.RESTAURANTE_B;

    @BeforeEach
    void setUp() {
        restauranteRepository = mock(RestauranteRepository.class);
        skuService = new SkuService(restauranteRepository);
    }

    private Restaurante restaurante(UUID id, String nombre, String codigo, String iniciales) {
        Restaurante r = new Restaurante();
        r.setId(id);
        r.setNombre(nombre);
        r.setCodigo(codigo);
        r.setIniciales(iniciales);
        r.setActivo(true);
        return r;
    }


    @Test
    @DisplayName("Genera el SKU con el formato INICIALES-CODIGO-CONSECUTIVO")
    void generaSkuCompleto() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB")));
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(1L);

        assertEquals("EB-R001-000001", skuService.generarSku(RESTAURANTE_1));
    }

    @Test
    @DisplayName("Consumir el consecutivo avanza el valor en la base de datos")
    void consumaConsecutivo() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB")));

        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1))
                .thenReturn(1L, 2L, 3L);

        assertEquals("EB-R001-000001", skuService.generarSku(RESTAURANTE_1));
        assertEquals("EB-R001-000002", skuService.generarSku(RESTAURANTE_1));
        assertEquals("EB-R001-000003", skuService.generarSku(RESTAURANTE_1));

        verify(restauranteRepository, times(3)).siguienteConsecutivoProducto(RESTAURANTE_1);
    }


    @Test
    @DisplayName("Si falta el código, lo toma de la secuencia de restaurantes")
    void asignaCodigoDesdeSecuencia() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", null, null)));
        when(restauranteRepository.siguienteNumeroCodigoRestaurante()).thenReturn(1L);
        when(restauranteRepository.existsByCodigo("R001")).thenReturn(false);
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(1L);

        String sku = skuService.generarSku(RESTAURANTE_1);

        assertEquals("EB-R001-000001", sku);
        verify(restauranteRepository, org.mockito.Mockito.atLeastOnce()).save(any(Restaurante.class));
    }

    @Test
    @DisplayName("El código NO se recalcula si ya existe (se conserva el histórico)")
    void codigoNoSeRecalcula() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Nombre Nuevo Total", "R001", "EB")));
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(5L);

        assertEquals("EB-R001-000005", skuService.generarSku(RESTAURANTE_1));

        verify(restauranteRepository, never()).save(any(Restaurante.class));
    }

    @Test
    @DisplayName("Las iniciales NO se recalculan aunque cambie el nombre")
    void inicialesNoSeRecalculan() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Nuevo Total Nombre", "R001", "EB")));
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(1L);

        assertEquals("EB-R001-000001", skuService.generarSku(RESTAURANTE_1));
    }

    @Test
    @DisplayName("Dos restaurantes distintos nunca comparten SKU")
    void restaurantesDistintosNoCompartenSku() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa Sabor", "R001", "EBS")));
        when(restauranteRepository.findById(RESTAURANTE_2))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_2, "El Buen Sabor", "R002", "EBS")));

        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(1L);
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_2)).thenReturn(1L);

        String sku1 = skuService.generarSku(RESTAURANTE_1);
        String sku2 = skuService.generarSku(RESTAURANTE_2);

        assertEquals("EBS-R001-000001", sku1);
        assertEquals("EBS-R002-000001", sku2);
        assertNotEquals(sku1, sku2);
    }

    @Test
    @DisplayName("Si el código está ocupado, se reintenta con el siguiente número")
    void codigoEnUsoSeReintenta() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", null, "EB")));

        when(restauranteRepository.siguienteNumeroCodigoRestaurante()).thenReturn(1L, 2L);
        when(restauranteRepository.existsByCodigo("R001")).thenReturn(true);
        when(restauranteRepository.existsByCodigo("R002")).thenReturn(false);
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(1L);

        assertEquals("EB-R002-000001", skuService.generarSku(RESTAURANTE_1));
    }

    @Test
    @DisplayName("Si la secuencia no entrega número, el error indica qué revisar")
    void secuenciaSinNumeroSeRechaza() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", null, "EB")));
        when(restauranteRepository.siguienteNumeroCodigoRestaurante()).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> skuService.generarSku(RESTAURANTE_1));

        assertTrue(ex.getMessage().contains("restaurante_codigo_seq"));
    }


    @Test
    @DisplayName("Solicitudes simultáneas sobre el MISMO restaurante no repiten consecutivo")
    void concurrenciaNoGeneraDuplicados() throws Exception {
        Restaurante r = restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB");
        when(restauranteRepository.findById(RESTAURANTE_1)).thenReturn(Optional.of(r));

        AtomicLong contador = new AtomicLong(0);
        java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();

        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenAnswer(inv -> {
            lock.lock();
            try {
                return contador.incrementAndGet();
            } finally {
                lock.unlock();
            }
        });

        int peticiones = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch arranque = new CountDownLatch(1);
        java.util.Set<String> skus = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.atomic.AtomicInteger errores =
                new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < peticiones; i++) {
            pool.submit(() -> {
                try {
                    arranque.await();
                    skus.add(skuService.generarSku(RESTAURANTE_1));
                } catch (Exception e) {
                    errores.incrementAndGet();
                }
            });
        }

        arranque.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, errores.get(), "No debe haber errores");
        assertEquals(peticiones, skus.size(),
                "Cada petición simultánea debe obtener un SKU distinto");
    }

    @Test
    @DisplayName("Restaurantes distintos avanzan su contador de forma independiente")
    void contadoresIndependientes() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB")));
        when(restauranteRepository.findById(RESTAURANTE_2))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_2, "El Buen Sabor", "R002", "EBS")));

        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1))
                .thenReturn(1L, 2L);
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_2))
                .thenReturn(1L);

        assertEquals("EB-R001-000001", skuService.generarSku(RESTAURANTE_1));
        assertEquals("EBS-R002-000001", skuService.generarSku(RESTAURANTE_2));
        assertEquals("EB-R001-000002", skuService.generarSku(RESTAURANTE_1));

        verify(restauranteRepository, times(2)).siguienteConsecutivoProducto(RESTAURANTE_1);
        verify(restauranteRepository, times(1)).siguienteConsecutivoProducto(RESTAURANTE_2);
    }


    @Test
    @DisplayName("Restaurante inexistente: error claro")
    void restauranteInexistente() {
        when(restauranteRepository.findById(RESTAURANTE_1)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> skuService.generarSku(RESTAURANTE_1));

        assertTrue(ex.getMessage().contains("restaurante"));
    }

    @Test
    @DisplayName("Restaurante null: error claro")
    void restauranteNull() {
        assertThrows(IllegalArgumentException.class, () -> skuService.generarSku(null));
    }

    @Test
    @DisplayName("Consecutivo no devuelto: error claro y accionable")
    void consecutivoNoDevuelto() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB")));
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1)).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> skuService.generarSku(RESTAURANTE_1));

        assertTrue(ex.getMessage().contains("producto_sku_consecutivos"));
    }

    @Test
    @DisplayName("Violación de integridad del contador: error claro")
    void violacionIntegridadContador() {
        when(restauranteRepository.findById(RESTAURANTE_1))
                .thenReturn(Optional.of(restaurante(RESTAURANTE_1, "Estación Brasa", "R001", "EB")));
        when(restauranteRepository.siguienteConsecutivoProducto(RESTAURANTE_1))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("violacion"));

        assertThrows(IllegalStateException.class, () -> skuService.generarSku(RESTAURANTE_1));
    }


    @Test
    @DisplayName("El estado de migración detecta restaurantes pendientes")
    void estadoMigracion() {
        when(restauranteRepository.countByCodigoIsNull()).thenReturn(2L);
        when(restauranteRepository.countByInicialesIsNull()).thenReturn(1L);

        SkuService.EstadoSku estado = skuService.consultarEstado();

        assertEquals(2L, estado.restaurantesSinCodigo());
        assertEquals(1L, estado.restaurantesSinIniciales());
        assertTrue(!estado.completo());
    }

    @Test
    @DisplayName("El estado de migración se marca completo sin pendientes")
    void estadoMigracionCompleta() {
        when(restauranteRepository.countByCodigoIsNull()).thenReturn(0L);
        when(restauranteRepository.countByInicialesIsNull()).thenReturn(0L);

        assertTrue(skuService.consultarEstado().completo());
    }
}
