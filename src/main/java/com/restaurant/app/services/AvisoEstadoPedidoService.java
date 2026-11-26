package com.restaurant.app.services;

import com.restaurant.app.model.Pedido;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * RF016: avisa por correo al cliente cuando cambia el estado de su pedido.
 * Solo se envía si el pedido tiene correo y el servidor de correo está
 * configurado; el envío ocurre después del commit y en segundo plano, así que
 * nunca bloquea ni revierte el cambio de estado.
 */
@Service
public class AvisoEstadoPedidoService {

    private static final Map<String, String> MENSAJES = Map.of(
            "CONFIRMADO", "Recibimos tu pedido y ya está confirmado.",
            "EN_PREPARACION", "Tu pedido se está preparando en cocina.",
            "LISTO", "¡Tu pedido está listo!",
            "ENTREGADO", "Tu pedido fue entregado. ¡Gracias por tu compra!",
            "CANCELADO", "Tu pedido fue cancelado.");

    private final CorreoService correoService;

    public AvisoEstadoPedidoService(CorreoService correoService) {
        this.correoService = correoService;
    }

    public void avisarCambio(Pedido pedido) {
        if (pedido == null || pedido.getCorreo() == null || pedido.getCorreo().isBlank()) {
            return;
        }
        String mensaje = MENSAJES.get(pedido.getEstado());
        if (mensaje == null) {
            return;
        }
        String correo = pedido.getCorreo();
        String numero = pedido.getNumeroSeguimiento() != null ? pedido.getNumeroSeguimiento() : "";
        StringBuilder texto = new StringBuilder()
                .append("Hola ").append(pedido.getNombre() != null ? pedido.getNombre() : "").append(",\n\n")
                .append(mensaje).append("\n\n")
                .append("Pedido: ").append(numero).append('\n');
        if ("CANCELADO".equals(pedido.getEstado()) && pedido.getMotivoCancelacion() != null) {
            texto.append("Motivo: ").append(pedido.getMotivoCancelacion()).append('\n');
        }
        texto.append("\nEste es un mensaje automático de NexDine.");
        String asunto = "Pedido " + numero + ": " + mensaje;

        Runnable envio = () -> correoService.enviarEnSegundoPlano(correo, asunto, texto.toString());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }
}
