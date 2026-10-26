package com.restaurant.app.services;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PedidoEventosService {

    private static final Logger logger = LoggerFactory.getLogger(PedidoEventosService.class);
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> suscriptores = new ConcurrentHashMap<>();
    private final Set<SseEmitter> conexionesActivas = ConcurrentHashMap.newKeySet();

    public SseEmitter suscribir(List<UUID> sucursalIds) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Set<UUID> sucursales = Set.copyOf(sucursalIds);
        conexionesActivas.add(emitter);
        sucursales.forEach(sucursalId ->
                suscriptores.computeIfAbsent(sucursalId, ignored -> new CopyOnWriteArrayList<>()).add(emitter));
        emitter.onCompletion(() -> quitar(sucursales, emitter));
        emitter.onTimeout(() -> quitar(sucursales, emitter));
        emitter.onError(error -> quitar(sucursales, emitter));
        try {
            emitter.send(SseEmitter.event().name("conectado").data(Map.of("sucursalIds", sucursales)));
        } catch (IOException | IllegalStateException e) {
            quitar(sucursales, emitter);
            emitter.completeWithError(e);
            logger.warn("No se pudo iniciar el flujo SSE de pedidos.", e);
        }
        return emitter;
    }

    public void publicar(UUID sucursalId, Map<String, Object> pedido) {
        CopyOnWriteArrayList<SseEmitter> emitters = suscriptores.get(sucursalId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("pedido-actualizado").data(pedido));
            } catch (IOException | IllegalStateException e) {
                quitarDeTodasLasSucursales(emitter);
                emitter.completeWithError(e);
                logger.warn("Se cerró una conexión SSE de pedidos que ya no estaba disponible.", e);
            }
        }
    }

    @Scheduled(fixedRate = 20000)
    public void mantenerConexionesActivas() {
        for (SseEmitter emitter : conexionesActivas) {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
            } catch (IOException | IllegalStateException e) {
                quitarDeTodasLasSucursales(emitter);
                emitter.completeWithError(e);
                logger.warn("Se cerró una conexión SSE de pedidos que ya no estaba disponible.", e);
            }
        }
    }

    private void quitar(Set<UUID> sucursalIds, SseEmitter emitter) {
        sucursalIds.forEach(sucursalId -> quitar(sucursalId, emitter));
        conexionesActivas.remove(emitter);
    }

    private void quitarDeTodasLasSucursales(SseEmitter emitter) {
        suscriptores.keySet().forEach(sucursalId -> quitar(sucursalId, emitter));
        conexionesActivas.remove(emitter);
    }

    private void quitar(UUID sucursalId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = suscriptores.get(sucursalId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                suscriptores.remove(sucursalId, emitters);
            }
        }
    }
}
