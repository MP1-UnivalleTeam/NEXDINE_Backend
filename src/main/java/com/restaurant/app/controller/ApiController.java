package com.restaurant.app.controller;

import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.services.MenuService;
import com.restaurant.app.services.MesaService;
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

    @Autowired
    private MenuService menuService;

    @Autowired
    private MesaService mesaService;

    @Autowired
    private RestauranteRepository restauranteRepository;

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

    // RF001 - Acceso al menú mediante QR
    // El parámetro "mesa" transporta el CODIGO opaco de la mesa (mesa-xxxxxxxx),
    // no su id numérico. Se valida contra la tabla mesas.
    @GetMapping("/menu")
    public ResponseEntity<?> verMenu(@RequestParam String mesa) {
        Optional<Mesa> mesaOpt = mesaService.buscarPorCodigoActiva(mesa);

        if (mesaOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "QR inválido",
                    "detalle", "El código QR no corresponde a una mesa válida o la mesa está inactiva."
            ));
        }

        Mesa mesaValida = mesaOpt.get();

        try {
            // La sesión se asocia al código de la mesa
            SesionMesa sesion = sesionMesaService.crearSesion(mesaValida.getCodigo());

            Map<String, Object> response = new HashMap<>();
            response.put("mesa", mesaValida.getNombre());
            response.put("codigoMesa", mesaValida.getCodigo());
            response.put("token", sesion.getToken());
            // Menú según la disponibilidad real de la sucursal de la mesa (RF007)
            response.put("menu", menuService
                    .getMenuPorSucursal(mesaValida.getSucursalId())
                    .get("categorias"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

    // RF002 - Enlace digital público del restaurante: /api/menu/{slug}
// NO requiere sesión HTTP, NO crea SesionMesa, NO genera token.
    @GetMapping("/menu/{slug}")
    public ResponseEntity<?> menuPublico(@PathVariable String slug) {
        Optional<Restaurante> restauranteOpt = restauranteRepository
                .findBySlugAndActivoTrue(slug);

        if (restauranteOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Menú no disponible",
                    "detalle", "El enlace del menú no corresponde a un restaurante disponible."
            ));
        }

        Restaurante restaurante = restauranteOpt.get();

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("restaurante", restaurante.getNombre());
            response.put("slug", restaurante.getSlug());
            response.put("menu", menuService.getMenuPublicoPorRestaurante(restaurante.getId()).get("categorias"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

    // FASE 11 - Actualización automática del menú (polling).
    // NO crea sesión, NO renueva la actividad: solo lee el catálogo.
    // Responde 401 si la sesión expiró para disparar el flujo de expiración.
    @GetMapping("/menu/por-sesion")
    public ResponseEntity<?> menuPorSesion(@RequestParam String token) {

        // Validar SIN renovar la actividad de la sesión
        if (!sesionMesaService.esSesionVigente(token)) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "SESION_EXPIRADA",
                    "mensaje", "Tu sesión ha expirado. Escanea nuevamente el código QR."
            ));
        }

        Optional<String> codigoMesaOpt = sesionMesaService.obtenerMesaDeSesion(token);
        if (codigoMesaOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "SESION_EXPIRADA",
                    "mensaje", "Tu sesión ha expirado. Escanea nuevamente el código QR."
            ));
        }

        // La sesión es válida: se resuelve la mesa y su sucursal para
        // devolver el menú con la disponibilidad real de esa sede (RF007).
        Optional<Mesa> mesaOpt = mesaService.buscarPorCodigoActiva(codigoMesaOpt.get());

        if (mesaOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "QR inválido",
                    "detalle", "La mesa ya no está disponible."
            ));
        }

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("mesa", mesaOpt.get().getNombre());
            response.put("menu", menuService
                    .getMenuPorSucursal(mesaOpt.get().getSucursalId())
                    .get("categorias"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

    // RF022 - Valida la sesión temporal. Responde 401 si expiró.
    @GetMapping("/mesa/sesion/estado")
    public ResponseEntity<?> estadoSesion(@RequestParam String token) {
        boolean valida = sesionMesaService.validarSesion(token);

        if (!valida) {
            return ResponseEntity.status(401).body(Map.of(
                    "valida", false,
                    "error", "SESION_EXPIRADA",
                    "mensaje", "Tu sesión ha expirado. Escanea nuevamente el código QR."
            ));
        }

        return ResponseEntity.ok(Map.of("valida", true));
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

}
