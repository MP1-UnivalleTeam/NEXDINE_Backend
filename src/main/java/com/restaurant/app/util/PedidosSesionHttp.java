package com.restaurant.app.util;

import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class PedidosSesionHttp {

    public static final String ATTRIBUTE = "pedidosInvitado";
    public static final int MAX_PEDIDOS = 50;

    private PedidosSesionHttp() {
    }

    public static void registrar(HttpSession session, UUID pedidoId) {
        if (session == null || pedidoId == null) {
            return;
        }
        Set<UUID> ids = ids(session);
        ids.add(pedidoId);
        while (ids.size() > MAX_PEDIDOS) {
            ids.remove(ids.iterator().next());
        }
        session.setAttribute(ATTRIBUTE, ids);
    }

    public static boolean esPropietario(HttpSession session, UUID pedidoId) {
        if (session == null || pedidoId == null) {
            return false;
        }
        return ids(session).contains(pedidoId);
    }

    @SuppressWarnings("unchecked")
    public static Set<UUID> ids(HttpSession session) {
        if (session == null) {
            return new LinkedHashSet<>();
        }
        Object pedidos = session.getAttribute(ATTRIBUTE);
        Set<UUID> ids = new LinkedHashSet<>();
        if (pedidos instanceof Set<?> existentes) {
            existentes.stream()
                    .filter(UUID.class::isInstance)
                    .map(UUID.class::cast)
                    .forEach(ids::add);
        }
        return ids;
    }
}
