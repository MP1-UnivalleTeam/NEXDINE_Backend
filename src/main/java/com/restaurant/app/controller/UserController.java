package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<?> getUsers(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/search")
    public ResponseEntity<?> consultarUsuario(
            @RequestParam @NonNull Long id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    public ResponseEntity<?> agregarUsuario(
            @RequestParam String nombre,
            @RequestParam String contraseña,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        if ("ADMINISTRADOR".equals(rol)) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "No se puede crear otro administrador desde este endpoint"));
        }

        return ResponseEntity.ok(
                userService.createUser(
                        nombre,
                        contraseña,
                        rol,
                        celular,
                        direccion
                )
        );
    }

    @PostMapping("/edit")
    public ResponseEntity<?> editarUsuario(
            @RequestParam @NonNull Long id,
            @RequestParam String nombre,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        userService.updateUser(id, nombre, rol, celular, direccion);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<?> eliminarUsuario(
            @RequestParam @NonNull Long id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> cambiarEstadoUsuario(
            @RequestParam @NonNull Long id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        userService.toggleUserStatus(id);
        return ResponseEntity.ok().build();
    }
}
