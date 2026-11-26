package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CorreoService;
import com.restaurant.app.services.StripePaymentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Traduce las excepciones de los servicios a respuestas HTTP con el mismo
 * criterio que el resto de controladores: 401 sin sesión, 400 datos
 * inválidos, 403 sin permisos y 503/502 cuando falla un servicio externo.
 */
final class RespuestasApi {

    private RespuestasApi() {
    }

    static Usuario usuario(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        return usuario instanceof Usuario u ? u : null;
    }

    static ResponseEntity<?> ejecutar(Usuario usuario, Supplier<?> accion) {
        if (usuario == null) {
            return ResponseEntity.status(401).build();
        }
        return ejecutarSinSesion(accion);
    }

    static ResponseEntity<?> ejecutarSinSesion(Supplier<?> accion) {
        try {
            Object resultado = accion.get();
            return resultado == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(resultado);
        } catch (CorreoService.CorreoNoConfiguradoException | StripePaymentService.StripeNoConfiguradoException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage(), "codigo", "NO_CONFIGURADO"));
        } catch (CorreoService.CorreoFallidoException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
