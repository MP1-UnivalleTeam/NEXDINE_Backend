package com.restaurant.app.controller;

import com.restaurant.app.dto.RegistroClienteRequest;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(
            @RequestParam String nombre,
            @RequestParam String contraseña,
            HttpSession session) {

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

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> registrarCliente(@RequestBody RegistroClienteRequest solicitud,
                                               HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Cierre la sesión actual antes de registrar una cuenta."));
        }
        if (solicitud == null || solicitud.nombre() == null
                || solicitud.nombre().isBlank()
                || solicitud.contrasena() == null
                || solicitud.contrasena().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre y la contraseña son obligatorios."));
        }
        String nombre = solicitud.nombre().trim();
        if (nombre.length() < 3 || nombre.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre debe tener entre 3 y 100 caracteres."));
        }
        if (solicitud.contrasena().length() < 6
                || solicitud.contrasena().length() > 255) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña debe tener entre 6 y 255 caracteres."));
        }
        if (userService.existeNombre(nombre)) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Ya existe un usuario con ese nombre."));
        }

        try {
            Usuario cliente = userService.createUser(
                    nombre,
                    solicitud.contrasena(),
                    "CLIENTE",
                    solicitud.celular(),
                    solicitud.direccion(),
                    null);
            if (solicitud.correo() != null && !solicitud.correo().isBlank()) {
                cliente.setCorreo(solicitud.correo().trim());
                cliente = userService.guardarUsuario(cliente);
            }
            session.setAttribute("currentUser", cliente);
            return ResponseEntity.status(201).body(cliente);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<?> cerrarSesion(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }
}
