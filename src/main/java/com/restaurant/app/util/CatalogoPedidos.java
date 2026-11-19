package com.restaurant.app.util;

import java.util.Map;
import java.util.Set;

public final class CatalogoPedidos {

    public static final String TIPO_MESA = "MESA";
    public static final String TIPO_DOMICILIO = "DOMICILIO";
    public static final String TIPO_PARA_LLEVAR = "PARA_LLEVAR";

    public static final Set<String> ESTADOS_PEDIDO = Set.of(
            "PENDIENTE", "CONFIRMADO", "EN_PREPARACION", "LISTO", "ENTREGADO", "CANCELADO");

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_CONFIRMADO = "CONFIRMADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";

    public static final Set<String> ESTADOS_FINALES = Set.of("ENTREGADO", "CANCELADO");

    public static final Map<String, Set<String>> TRANSICIONES_PEDIDO = Map.of(
            "PENDIENTE", Set.of("CONFIRMADO", "CANCELADO"),
            "CONFIRMADO", Set.of("EN_PREPARACION", "CANCELADO"),
            "EN_PREPARACION", Set.of("LISTO", "CANCELADO"),
            "LISTO", Set.of("ENTREGADO", "CANCELADO"),
            "ENTREGADO", Set.of(),
            "CANCELADO", Set.of());

    public static final String MESA_LIBRE = "LIBRE";
    public static final String MESA_OCUPADA = "OCUPADA";

    public static final String PAGO_PENDIENTE = "PENDIENTE";
    public static final String PAGO_PAGADO = "PAGADO";

    public static final String METODO_CONTRA_ENTREGA = "CONTRA_ENTREGA";
    public static final String METODO_EFECTIVO = "EFECTIVO";
    public static final String METODO_TARJETA = "TARJETA";

    public static final Set<String> METODOS_PAGO = Set.of(
            METODO_CONTRA_ENTREGA, METODO_EFECTIVO, METODO_TARJETA);

    public static final String NOTIFICACION_PAGO_EXITOSO = "PAGO_EXITOSO";
    public static final String DESTINATARIO_PERSONAL = "PERSONAL";
    public static final String DESTINATARIO_CLIENTE = "CLIENTE";

    private CatalogoPedidos() {
    }
}
