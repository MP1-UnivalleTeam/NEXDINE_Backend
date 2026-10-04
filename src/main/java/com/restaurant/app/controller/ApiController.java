package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private UserService userService;

    @GetMapping("/auth/check")
    public ResponseEntity<?> checkAuth(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("currentUser");
        if (usuario != null) {
            return ResponseEntity.ok(usuario);
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("currentUser");
        if (usuario != null) {
            return ResponseEntity.ok(usuario);
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<Usuario>> getAllUsers(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");
        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/search")
    public ResponseEntity<?> searchUser(@RequestParam Long id, HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");
        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuario = userService.getUserById(id);
        if (usuario.isPresent()) {
            return ResponseEntity.ok(usuario.get());
        }
        return ResponseEntity.notFound().build();
    }
}
