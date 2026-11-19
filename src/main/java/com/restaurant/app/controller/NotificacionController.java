package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.NotificacionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpSession session) {
        Object usuario = session.getAttribute("currentUser");
        if (!(usuario instanceof Usuario user)) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(notificacionService.listar(user));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
