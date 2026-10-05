package com.restaurant.app.controller;

import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.SesionMesaService;
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

    @Autowired
    private SesionMesaService sesionMesaService;

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

    // Tarea 1 y 2: Acceder al menú mediante QR o enlace
    @GetMapping("/menu")
    public ResponseEntity<?> verMenu(@RequestParam String mesa, HttpSession session) {
        SesionMesa sesion = sesionMesaService.crearSesion(mesa);

        Map<String, Object> response = new HashMap<>();
        response.put("mesa", mesa);
        response.put("token", sesion.getToken());
        response.put("menu", getMenuData());

        return ResponseEntity.ok(response);
    }

    // Tarea 3: Generar sesión temporal asociada a la mesa
    @PostMapping("/mesa/sesion")
    public ResponseEntity<?> crearSesionMesa(@RequestBody Map<String, String> body) {
        String idMesa = body.get("idMesa");
        if (idMesa == null || idMesa.isEmpty()) {
            return ResponseEntity.badRequest().body("idMesa es requerido");
        }

        SesionMesa sesion = sesionMesaService.crearSesion(idMesa);

        Map<String, Object> response = new HashMap<>();
        response.put("token", sesion.getToken());
        response.put("idMesa", sesion.getIdMesa());
        response.put("fechaCreacion", sesion.getFechaCreacion());

        return ResponseEntity.ok(response);
    }

    // Tarea 4: Validar sesión (verifica inactividad de 15 minutos)
    @GetMapping("/mesa/sesion/validar")
    public ResponseEntity<?> validarSesion(@RequestParam String token) {
        boolean valida = sesionMesaService.validarSesion(token);

        Map<String, Object> response = new HashMap<>();
        response.put("valida", valida);

        if (valida) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }

    // Cerrar sesión de mesa
    @PostMapping("/mesa/sesion/cerrar")
    public ResponseEntity<?> cerrarSesionMesa(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        sesionMesaService.cerrarSesion(token);
        return ResponseEntity.ok("Sesión cerrada");
    }

    private List<Map<String, Object>> getMenuData() {
        List<Map<String, Object>> menu = new java.util.ArrayList<>();

        menu.add(createMenuItem(1, "Hamburguesa", "Hamburguesa clásica con queso", 28900.0));
        menu.add(createMenuItem(2, "Pizza", "Pizza pepperoni", 35000.0));
        menu.add(createMenuItem(3, "Ensalada", "Ensalada César", 18000.0));
        menu.add(createMenuItem(4, "Pasta", "Pasta Alfredo", 25000.0));
        menu.add(createMenuItem(5, "Tacos", "Tacos de pollo (3 unidades)", 22000.0));
        menu.add(createMenuItem(6, "Bebidas", "Gaseosa, jugos naturales", 8000.0));

        return menu;
    }

    private Map<String, Object> createMenuItem(int id, String nombre, String descripcion, double precio) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("nombre", nombre);
        item.put("descripcion", descripcion);
        item.put("precio", precio);
        return item;
    }
}
