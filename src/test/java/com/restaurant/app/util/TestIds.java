package com.restaurant.app.util;

import org.springframework.lang.NonNull;

import java.util.Objects;
import java.util.UUID;

public final class TestIds {

    private TestIds() {
    }

    @NonNull
    public static final UUID RESTAURANTE_A = Objects.requireNonNull(UUID.fromString("11111111-1111-7111-8111-111111111111"));

    @NonNull
    public static final UUID RESTAURANTE_B = Objects.requireNonNull(UUID.fromString("22222222-2222-7222-8222-222222222222"));

    @NonNull
    public static final UUID SUCURSAL_A1 = Objects.requireNonNull(UUID.fromString("aaaaaaa1-1111-7111-8111-aaaaaaaa111"));

    @NonNull
    public static final UUID SUCURSAL_A2 = Objects.requireNonNull(UUID.fromString("aaaaaaa2-2222-7222-8222-aaaaaaaa222"));

    @NonNull
    public static final UUID SUCURSAL_B1 = Objects.requireNonNull(UUID.fromString("bbbbbbb1-3333-7333-8333-bbbbbbbb111"));

    @NonNull
    public static final UUID PRODUCTO_A = Objects.requireNonNull(UUID.fromString("ccccccc1-4444-7444-8444-cccccccc111"));

    @NonNull
    public static final UUID PRODUCTO_B = Objects.requireNonNull(UUID.fromString("ccccccc2-5554-7454-8454-cccccccc222"));

    @NonNull
    public static final UUID CATEGORIA_A = Objects.requireNonNull(UUID.fromString("ddddddd1-6666-7666-8666-dddddddd111"));

    @NonNull
    public static final UUID USUARIO_ADMIN_A = Objects.requireNonNull(UUID.fromString("eeeeeeee1-7777-7777-8777-eeeeeeee111"));

    @NonNull
    public static final UUID USUARIO_SUPER_A = Objects.requireNonNull(UUID.fromString("ffffffff1-8888-7888-8888-ffffffff111"));

    @NonNull
    public static UUID nuevo() {
        return Objects.requireNonNull(UUID.randomUUID());
    }
}