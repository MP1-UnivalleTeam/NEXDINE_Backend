package com.restaurant.app.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class UuidV7Generator {

    private UuidV7Generator() {
    }

    public static final int VERSION = 7;

    private static final int VERSION_NIBBLE = 0b0111;
    private static final int VARIANTE_RFC_4122 = 0b10;

    private static final int MAX_CONTADOR = 0x0FFF;

    private static final AtomicReference<Estado> ULTIMO = new AtomicReference<>(new Estado(-1L, -1L));

    private record Estado(long milisegundo, long contador) {
    }

    public static UUID nuevo() {
        return nuevo(System.currentTimeMillis());
    }

    public static UUID nuevo(long milisegundos) {
        long msb = 0L;
        long lsb = 0L;

        for (int desplazamiento = 40; desplazamiento >= 0; desplazamiento -= 8) {
            msb = (msb << 8) | ((milisegundos >>> desplazamiento) & 0xFFL);
        }

        long randA = siguienteContador(milisegundos) & 0x0FFFL;
        msb = (msb << 16) | ((long) VERSION_NIBBLE << 12) | randA;

        long randB = ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL;
        lsb = ((long) VARIANTE_RFC_4122 << 62) | randB;

        return new UUID(msb, lsb);
    }

    private static long siguienteContador(long milisegundos) {
        while (true) {
            Estado anterior = ULTIMO.get();

            long contador = (anterior.milisegundo() == milisegundos)
                    ? anterior.contador() + 1
                    : 0L;

            if (contador > MAX_CONTADOR) {
                contador = (MAX_CONTADOR + 1)
                        + ThreadLocalRandom.current().nextInt(MAX_CONTADOR + 1);
            }

            if (ULTIMO.compareAndSet(anterior, new Estado(milisegundos, contador))) {
                return contador;
            }
        }
    }

    public static long extraerTimestamp(UUID uuid) {
        if (uuid == null || uuid.version() != VERSION) {
            return -1L;
        }

        return (uuid.getMostSignificantBits() >>> 16) & 0xFFFFFFFFFFFFL;
    }
}
