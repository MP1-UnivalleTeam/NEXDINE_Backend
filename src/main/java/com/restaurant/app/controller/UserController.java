package com.restaurant.app.controller;

import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.repository.UserRepository;
import com.restaurant.app.services.UsuarioContextService;
import com.restaurant.app.services.UserService;

import jakarta.servlet.http.HttpSession;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UsuarioContextService usuarioContextService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    private boolean esSuperadmin(Usuario u) {
        return "SUPERADMIN".equals(u.getRol());
    }

    private boolean esAdminOSuper(Usuario u) {
        return "ADMINISTRADOR".equals(u.getRol()) || "SUPERADMIN".equals(u.getRol());
    }

    private ResponseEntity<?> noAutorizado() {
        return ResponseEntity.status(403).build();
    }

    // ------------------------------------------------------------------
    // Listados
    // ------------------------------------------------------------------

    /**
     * Lista usuarios visibles según el alcance del solicitante.
     *  · SUPERADMIN   → empleados de todas las sucursales de su restaurante
     *  · ADMINISTRADOR → empleados de su propia sucursal
     *  · CLIENTE      → 403
     */
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

            // ADMINISTRADOR: solo empleados operativos; nunca ADMINISTRADOR,
            // SUPERADMIN ni CLIENTE
            List<Usuario> filtrados = todos.stream()
                    .filter(u -> !"CLIENTE".equals(u.getRol()))
                    .filter(u -> !esSuperadmin(u))
                    .filter(u -> !"ADMINISTRADOR".equals(u.getRol()) || esSuperadmin(usuarioActual))
                    .toList();

            // ADMINISTRADOR: acotar a su sucursal
            if (!esSuperadmin(usuarioActual) && ctx.getSucursalId() != null) {
                Long sucursalAdmin = ctx.getSucursalId();
                List<Long> empleadosDeSucursal = usuarioContextService.listarIdsEmpleadosDeSucursal(sucursalAdmin);

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
            @RequestParam @NonNull Long id,
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

    /**
     * Sucursales que el solicitante puede asignar.
     * ADMINISTRADOR solo ve la suya; SUPERADMIN ve todas las de su restaurante.
     */
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

            if (ctx.getSucursalId() != null) {
                return ResponseEntity.ok(
                        sucursalRepository.findById(ctx.getSucursalId()).map(List::of).orElseGet(List::of));
            }

            return ResponseEntity.ok(sucursalRepository.findByRestauranteId(ctx.getRestauranteId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Crear usuario
    // ------------------------------------------------------------------

    /**
     * Creación de usuarios con reglas:
     *  · SUPERADMIN puede crear ADMINISTRADOR y empleados, elige sucursal.
     *  · ADMINISTRADOR solo crea empleados operativos, en SU sucursal.
     *  · ADMINISTRADOR nunca crea ADMINISTRADOR.
     */
    /**
     * Sucursal actualmente asignada a un usuario.
     * Permite al frontend mostrar el valor real al editar (RF018).
     */
@GetMapping("/{id}/sucursal")
    public ResponseEntity<?> sucursalDeUsuario(@PathVariable Long id, HttpSession session) {
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

        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede consultar la asignación de un SUPERADMIN."));
        }

        return ResponseEntity.ok(Map.of(
                "usuarioId", objetivo.get().getId(),
                "sucursalId", userService.obtenerSucursalAsignada(objetivo.get())
        ));
    }

    @PostMapping("/create")
    public ResponseEntity<?> agregarUsuario(
            @RequestParam String nombre,
            @RequestParam String contraseña,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            @RequestParam(required = false) Long sucursalId,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        // Usuario duplicado → 409, no 403
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

            Long sucursalEfectiva = ctx.getSucursalId();

            if (esSuperadmin(usuarioActual)) {
                // La sucursal es opcional: se puede crear un ADMINISTRADOR
                // sin asignar y asignarlo más adelante (RF018).
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
                // ADMINISTRADOR: se ignora cualquier sucursalId enviado
                if (sucursalId != null && !sucursalId.equals(ctx.getSucursalId())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "No puede asignar usuarios a otra sucursal."));
                }
            }

            // Solo CLIENTE queda sin sucursal. El resto usa la sucursal resuelta,
   // que puede ser null si el SUPERADMIN creó al usuario sin asignar.
            Long sucursalARegistrar = "CLIENTE".equals(rol) ? null : sucursalEfectiva;

            return ResponseEntity.ok(
                    userService.createUser(nombre, contraseña, rol, celular, direccion, sucursalARegistrar));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Modificar usuario
    // ------------------------------------------------------------------

    @PostMapping("/edit")
    public ResponseEntity<?> editarUsuario(
            @RequestParam @NonNull Long id,
            @RequestParam String nombre,
            @RequestParam String rol,
            @RequestParam String celular,
            @RequestParam String direccion,
            @RequestParam(required = false) Long sucursalId,
            @RequestParam(defaultValue = "false") boolean quitarSucursal,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!esAdminOSuper(usuarioActual)) {
            return noAutorizado();
        }

        // No se puede cambiar a SUPERADMIN por este endpoint
        if ("SUPERADMIN".equals(rol)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se puede asignar el rol SUPERADMIN desde este endpoint."));
        }

        // ADMINISTRADOR no puede crear/convertir en ADMINISTRADOR
        if ("ADMINISTRADOR".equals(rol) && !esSuperadmin(usuarioActual)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede crear ADMINISTRADORES."));
        }

        Optional<Usuario> objetivo = userService.getUserById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Nadie puede degradar al SUPERADMIN
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede modificar un SUPERADMIN."));
        }

        // ADMINISTRADOR no puede modificar a otro ADMINISTRADOR
        if (!esSuperadmin(usuarioActual)
                && ("ADMINISTRADOR".equals(objetivo.get().getRol())
                    || objetivo.get().getId().equals(usuarioActual.getId()))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para modificar este usuario."));
        }

        // ADMINISTRADOR no puede mover empleados a otra sucursal
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

        try {
            // ADMINISTRADOR nunca envía sucursalId ni quitarSucursal: se ignoran
            Long sucursalEfectiva = esSuperadmin(usuarioActual) ? sucursalId : null;
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

    // ------------------------------------------------------------------
    // Activar / Suspender
    // ------------------------------------------------------------------

    @PostMapping("/toggle")
    public ResponseEntity<?> cambiarEstadoUsuario(
            @RequestParam @NonNull Long id,
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

        // El SUPERADMIN no se puede suspender a sí mismo ni ser modificado
        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede cambiar el estado de un SUPERADMIN."));
        }

        // ADMINISTRADOR no toca a otros ADMINISTRADORES ni a sí mismo
        if (!esSuperadmin(usuarioActual)
                && ("ADMINISTRADOR".equals(objetivo.get().getRol())
                    || objetivo.get().getId().equals(usuarioActual.getId()))) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos sobre este usuario."));
        }

        // ADMINISTRADOR solo opera sobre empleados de su sucursal
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

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    @PostMapping("/delete")
    public ResponseEntity<?> eliminarUsuario(
            @RequestParam @NonNull Long id,
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

        if (esSuperadmin(objetivo.get())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No se puede eliminar un SUPERADMIN."));
        }

        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}