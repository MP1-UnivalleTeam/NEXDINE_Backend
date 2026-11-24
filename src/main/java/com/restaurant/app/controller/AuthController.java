package com.restaurant.app.controller;

import com.restaurant.app.dto.RegistroClienteRequest;
import com.restaurant.app.dto.RegistroDuenoRequest;
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
        if (solicitud == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre y la contraseña son obligatorios."));
        }
        return registrarCuenta(solicitud.nombre(), solicitud.contrasena(), solicitud.celular(),
                solicitud.direccion(), solicitud.correo(), session, "CLIENTE");
    }

    @PostMapping("/api/auth/register/dueño")
    public ResponseEntity<?> registrarDueno(@RequestBody RegistroDuenoRequest solicitud, HttpSession session) {
        if (solicitud == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre y la contraseña son obligatorios."));
        }
        return registrarCuenta(solicitud.nombre(), solicitud.contrasena(), solicitud.celular(),
                solicitud.direccion(), solicitud.correo(), session, "ADMINISTRADOR");
    }

    private ResponseEntity<?> registrarCuenta(String nombre, String contrasena, String celular,
                                              String direccion, String correo, HttpSession session,
                                              String rol) {
        if (session.getAttribute("currentUser") != null) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Cierre la sesión actual antes de registrar una cuenta."));
        }
        if (nombre == null || nombre.isBlank()
                || contrasena == null
                || contrasena.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre y la contraseña son obligatorios."));
        }
        String nombreNormalizado = nombre.trim();
        if (nombreNormalizado.length() < 3 || nombreNormalizado.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre debe tener entre 3 y 100 caracteres."));
        }
        if (contrasena.length() < 6
                || contrasena.length() > 255) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La contraseña debe tener entre 6 y 255 caracteres."));
        }
        if (userService.existeNombre(nombreNormalizado)) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Ya existe un usuario con ese nombre."));
        }

        try {
            Usuario usuario = userService.createUser(
                    nombreNormalizado,
                    contrasena,
                    rol,
                    celular,
                    direccion,
                    null);
            if (correo != null && !correo.isBlank()) {
                usuario.setCorreo(correo.trim());
                usuario = userService.guardarUsuario(usuario);
            }
            session.setAttribute("currentUser", usuario);
            return ResponseEntity.status(201).body(usuario);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible registrar la cuenta."));
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<?> cerrarSesion(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }
}
