package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(
            @RequestParam String nombre,
            @RequestParam String contraseña,
            HttpSession session) {

        // Crea el administrador inicial si todavía no existe.
        userService.initAdmin();

        Optional<Usuario> usuarioOpt = userService.login(nombre, contraseña);

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        Usuario usuario = usuarioOpt.get();

        if (usuario instanceof UserService.SuspendedMarker) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Usuario suspendido"));
        }

        session.setAttribute("currentUser", usuario);

        return ResponseEntity.ok(usuario);
    }

    @GetMapping("/logout")
    public ResponseEntity<?> cerrarSesion(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }
}
