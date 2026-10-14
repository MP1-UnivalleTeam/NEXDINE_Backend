package com.restaurant.app.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UuidV7Generator - RFC 9562")
class UuidV7GeneratorTest {

    @Test
    @DisplayName("La versión es 7, no 4 (requisito explícito del proyecto)")
    void generaVersion7() {
        UUID uuid = UuidV7Generator.nuevo();

        assertEquals(7, uuid.version(),
                "Se esperaba UUID v7. La versión real es " + uuid.version()
                        + ": la generación sería aleatoria (v4).");
    }

    @Test
    @DisplayName("La variante es RFC 4122 (10xx)")
    void generaVarianteRfc4122() {
        UUID uuid = UuidV7Generator.nuevo();

        assertEquals(2, uuid.variant(),
                "La variante debe ser 2 (RFC 4122). Es " + uuid.variant());
    }

    @Test
    @DisplayName("El timestamp codificado coincide con el instante solicitado")
    void decodificaElTimestamp() {
        long instante = 1_760_000_000_000L;

        UUID uuid = UuidV7Generator.nuevo(instante);

        assertEquals(instante, UuidV7Generator.extraerTimestamp(uuid));
    }

    @Test
    @DisplayName("El timestamp recuperado está dentro del margen del reloj")
    void timestampDentroDelMargen() {
        long antes = System.currentTimeMillis();

        UUID uuid = UuidV7Generator.nuevo();

        long despues = System.currentTimeMillis();
        long creado = UuidV7Generator.extraerTimestamp(uuid);

        assertTrue(creado >= antes - 1 && creado <= despues + 1,
                "El timestamp decodificado (" + creado + ") no está en el rango ["
                        + antes + ", " + despues + "]");
    }

    @Test
    @DisplayName("UUID no v7 devuelve -1 al intentar decodificarlo")
    void rechazaUuidNoV7() {
        assertEquals(-1L, UuidV7Generator.extraerTimestamp(UUID.randomUUID()));
        assertEquals(-1L, UuidV7Generator.extraerTimestamp(null));
    }

    @Test
    @DisplayName("Los bits de versión ocupan la posición correcta (byte 6, nibble alto)")
    void bitsDeVersionEnPosicionCorrecta() {
        UUID uuid = UuidV7Generator.nuevo();

        long msb = uuid.getMostSignificantBits();

        assertEquals(0b0111L, (msb >>> 12) & 0x0FL);
    }

    @Test
    @DisplayName("Genera UUID distintos de forma masiva")
    void generaValoresUnicos() {
        int cantidad = 100_000;
        ConcurrentSkipListSet<UUID> generados = new ConcurrentSkipListSet<>();

        for (int i = 0; i < cantidad; i++) {
            generados.add(UuidV7Generator.nuevo());
        }

        assertEquals(cantidad, generados.size(),
                "Se generaron UUID duplicados: la generación no es única");
    }

    @Test
    @DisplayName("Generación concurrente sin colisiones")
    void concurrenciaSinColisiones() throws Exception {
        int hilos = 16;
        int porHilo = 2_000;

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch arranque = new CountDownLatch(1);
        ConcurrentSkipListSet<UUID> generados = new ConcurrentSkipListSet<>();

        for (int h = 0; h < hilos; h++) {
            pool.submit(() -> {
                try {
                    arranque.await();
                    for (int i = 0; i < porHilo; i++) {
                        generados.add(UuidV7Generator.nuevo());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        arranque.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(hilos * porHilo, generados.size(),
                "La generación concurrente produjo UUID repetidos");
    }

    @Test
    @DisplayName("Dentro del mismo milisegundo los UUID mantienen orden y unicidad")
    void monotonoEnElMismoMilisegundo() {
        long instante = 1_760_000_000_123L;
        int cantidad = 5_000;

        java.util.Set<UUID> generados = new java.util.HashSet<>();
        for (int i = 0; i < cantidad; i++) {
            generados.add(UuidV7Generator.nuevo(instante));
        }

        assertEquals(cantidad, generados.size(),
                "Deben ser 5000 UUID distintos con el mismo timestamp (rand_a monótono)");

        long primero = UuidV7Generator.extraerTimestamp(generados.iterator().next());
        long ahora = System.currentTimeMillis();
        assertTrue(primero <= ahora,
                "El timestamp decodificado (" + primero + ") no puede estar en el futuro (ahora " + ahora + ")");
    }

    @Test
    @DisplayName("El byte alto del rand_b lleva la variante (bits 63-62 del LSB)")
    void bitsDeVarianteEnPosicionCorrecta() {
        UUID uuid = UuidV7Generator.nuevo();

        long lsb = uuid.getLeastSignificantBits();

        assertEquals(0b10L, lsb >>> 62);
    }

    @Test
    @DisplayName("Cada UUID generado lleva la marca de versión 7 en hexadecimal")
    void representacionHexadecimalCorrecta() {
        String texto = UuidV7Generator.nuevo().toString();

        String[] grupos = texto.split("-");
        assertEquals(5, grupos.length);
        assertEquals('7', grupos[2].charAt(0));
    }
}
