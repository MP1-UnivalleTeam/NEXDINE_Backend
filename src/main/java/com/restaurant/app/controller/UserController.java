package com.restaurant.app.controller;

import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.repository.UserRepository;
import com.restaurant.app.services.UsuarioContextService;
import com.restaurant.app.services.UserService;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UsuarioContextService usuarioContextService;
    private final UserRepository userRepository;
    private final SucursalRepository sucursalRepository;

    public UserController(UserService userService,
                          UsuarioContextService usuarioContextService,
                          UserRepository userRepository,
                          SucursalRepository sucursalRepository) {
        this.userService = userService;
        this.usuarioContextService = usuarioContextService;
        this.userRepository = userRepository;
        this.sucursalRepository = sucursalRepository;
    }

    private boolean esSuperadmin(Usuario u) {
        return "SUPERADMIN".equals(u.getRol());
    }

    private boolean esAdminOSuper(Usuario u) {
        return "ADMINISTRADOR".equals(u.getRol()) || "SUPERADMIN".equals(u.getRol());
    }

    private ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(403).build();
    }

    @GetMapping
    public ResponseEntity<?> getUsers(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        try {
            UsuarioContextService.UsuarioContext ctx = usuarioContextService.obtenerContexto(usuarioActual);

            List<Usuario> todos = userService.getUsuariosConSucursal();

            List<Usuario> filtrados = todos.stream()
                    .filter(u -> !"CLIENTE".equals(u.getRol()))
                    .filter(u -> !esSuperadmin(u))
                    .filter(u -> !"ADMINISTRADOR".equals(u.getRol()) || esSuperadmin(usuarioActual))
                    .toList();

            if (!esSuperadmin(usuarioActual) && ctx.getSucursalId() != null) {
                UUID sucursalAdmin = ctx.getSucursalId();
                List<UUID> empleadosDeSucursal = usuarioContextService.listarIdsEmpleadosDeSucursal(sucursalAdmin);

                filtrados = filtrados.stream()
                        .filter(u -> {
                            if ("ADMINISTRADOR".equals(u.getRol())) {
                                return u.getId().equals(usuarioActual.getId());
                            }
                            return empleadosDeSucursal.contains(u.getId());
                        })
                        .toList();
            }

            return ResponseEntity.ok(filtrados);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> consultarUsuario(
            @RequestParam @NonNull UUID id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sucursales")
    public ResponseEntity<?> listarSucursales(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        try {
            UsuarioContextService.UsuarioContext ctx = usuarioContextService.obtenerContexto(usuarioActual);

            UUID sucursalSolicitada = ctx.getSucursalId();

            if (sucursalSolicitada != null) {
                return ResponseEntity.ok(
                        sucursalRepository.findById(sucursalSolicitada).map(List::of).orElseGet(List::of));
            }

            return ResponseEntity.ok(sucursalRepository.findByRestauranteId(ctx.getRestauranteId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

@GetMapping("/{id}/sucursal")
    public ResponseEntity<?> sucursalDeUsuario(@PathVariable @NonNull UUID id, HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        Optional<Usuario> objetivo = userService.getUserById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            usuarioContextService.exigirUsuarioEnContexto(usuarioActual, objetivo.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede consultar la asignación de un SUPERADMIN."));
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("usuarioId", objetivo.get().getId());
        datos.put("sucursalId", userService.obtenerSucursalAsignada(objetivo.get()));
        return ResponseEntity.ok(datos);
    }

    @PostMapping("/create")
    public ResponseEntity<?> agregarUsuario(
            @RequestParam String nombre,
            @RequestParam String contraseña,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            @RequestParam(required = false) UUID sucursalId,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        if (userRepository.findByNombre(nombre).isPresent()) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "No se puede crear el usuario porque ya existe."));
        }

        if ("ADMINISTRADOR".equals(rol) && !esSuperadmin(usuarioActual)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede crear ADMINISTRADORES."));
        }

        if ("SUPERADMIN".equals(rol)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se puede crear un SUPERADMIN desde este endpoint."));
        }

        try {
            UsuarioContextService.UsuarioContext ctx = usuarioContextService.obtenerContexto(usuarioActual);

            UUID sucursalEfectiva = ctx.getSucursalId();

            if (esSuperadmin(usuarioActual)) {
                if (sucursalId != null) {
                    Sucursal sucursal = sucursalRepository.findById(sucursalId)
                            .orElseThrow(() -> new IllegalArgumentException("La sucursal indicada no existe."));
                    if (!sucursal.getRestauranteId().equals(ctx.getRestauranteId())) {
                        return ResponseEntity.status(403)
                                .body(Map.of("error", "La sucursal indicada no pertenece a su restaurante."));
                    }
                    sucursalEfectiva = sucursalId;
                } else {
                    sucursalEfectiva = null;
                }
            } else {
                if (sucursalId != null && !sucursalId.equals(ctx.getSucursalId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "No puede asignar usuarios a otra sucursal."));
                }
            }

            UUID sucursalARegistrar = "CLIENTE".equals(rol) ? null : sucursalEfectiva;

            return ResponseEntity.ok(
                    userService.createUser(nombre, contraseña, rol, celular, direccion, sucursalARegistrar));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/edit")
    public ResponseEntity<?> editarUsuario(
            @RequestParam @NonNull UUID id,
            @RequestParam String nombre,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            @RequestParam(required = false) UUID sucursalId,
            @RequestParam(defaultValue = "false") boolean quitarSucursal,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        if ("SUPERADMIN".equals(rol)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se puede asignar el rol SUPERADMIN desde este endpoint."));
        }

        if ("ADMINISTRADOR".equals(rol) && !esSuperadmin(usuarioActual)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede crear ADMINISTRADORES."));
        }

        Optional<Usuario> objetivo = userService.getUserById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            usuarioContextService.exigirUsuarioEnContexto(usuarioActual, objetivo.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede modificar un SUPERADMIN."));
        }

        if (!esSuperadmin(usuarioActual)
                && ("ADMINISTRADOR".equals(objetivo.get().getRol())
                    || objetivo.get().getId().equals(usuarioActual.getId()))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para modificar este usuario."));
        }

        if (!esSuperadmin(usuarioActual) && sucursalId != null) {
            try {
                UsuarioContextService.UsuarioContext ctx = usuarioContextService.obtenerContexto(usuarioActual);
                if (!sucursalId.equals(ctx.getSucursalId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "No puede asignar usuarios a otra sucursal."));
                }
            } catch (IllegalStateException e) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
        }

        if (sucursalId != null) {
            try {
                usuarioContextService.exigirSucursalDelContexto(usuarioActual, sucursalId);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
        }

        try {
            usuarioContextService.exigirUsuarioEnContexto(usuarioActual, objetivo.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }

        try {
            UUID sucursalEfectiva = esSuperadmin(usuarioActual) ? sucursalId : null;
            boolean quitarEfectivo = esSuperadmin(usuarioActual) && quitarSucursal;
            userService.updateUser(id, nombre, rol, celular, direccion, sucursalEfectiva, quitarEfectivo);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar el usuario: " + e.getMessage()));
        }
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> cambiarEstadoUsuario(
            @RequestParam @NonNull UUID id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        Optional<Usuario> objetivo = userService.getUserById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            usuarioContextService.exigirUsuarioEnContexto(usuarioActual, objetivo.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede cambiar el estado de un SUPERADMIN."));
        }

        if (!esSuperadmin(usuarioActual)
                && ("ADMINISTRADOR".equals(objetivo.get().getRol())
                    || objetivo.get().getId().equals(usuarioActual.getId()))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos sobre este usuario."));
        }

        if (!esSuperadmin(usuarioActual)) {
            try {
                UsuarioContextService.UsuarioContext ctx = usuarioContextService.obtenerContexto(usuarioActual);
                boolean pertenece = usuarioContextService
                        .empleadoPerteneceASucursal(id, ctx.getSucursalId());
                if (!pertenece) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "El empleado pertenece a otra sucursal."));
                }
            } catch (IllegalStateException e) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            }
        }

        userService.toggleUserStatus(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<?> eliminarUsuario(
            @RequestParam @NonNull UUID id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esSuperadmin(usuarioActual)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede eliminar usuarios."));
        }

        Optional<Usuario> objetivo = userService.getUserById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            usuarioContextService.exigirUsuarioEnContexto(usuarioActual, objetivo.get());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede eliminar un SUPERADMIN."));
        }

        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}
