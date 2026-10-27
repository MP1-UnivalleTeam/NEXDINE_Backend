package com.restaurant.app.controller;

import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.MenuService;
import com.restaurant.app.services.MesaService;
import com.restaurant.app.services.SesionMesaService;
import com.restaurant.app.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserService userService;
    private final SesionMesaService sesionMesaService;
    private final MenuService menuService;
    private final MesaService mesaService;
    private final RestauranteRepository restauranteRepository;
    private final SucursalRepository sucursalRepository;
    private final BigDecimal tasaImpuesto;

    public ApiController(UserService userService,
                         SesionMesaService sesionMesaService,
                         MenuService menuService,
                         MesaService mesaService,
                         RestauranteRepository restauranteRepository,
                         SucursalRepository sucursalRepository,
                         @Value("${app.pedidos.tasa-impuesto:0}") BigDecimal tasaImpuesto) {
        this.userService = userService;
        this.sesionMesaService = sesionMesaService;
        this.menuService = menuService;
        this.mesaService = mesaService;
        this.restauranteRepository = restauranteRepository;
        this.sucursalRepository = sucursalRepository;
        this.tasaImpuesto = tasaImpuesto;
    }

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
    public ResponseEntity<?> searchUser(@RequestParam @NonNull UUID id, HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");
        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }
        Optional<Usuario> usuario = userService.getUserById(id);
        if (usuario.isPresent()) {
            Usuario encontrado = usuario.get();
            return ResponseEntity.ok(encontrado);
        }
        return ResponseEntity.notFound().build();
    }

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
            SesionMesa sesion = sesionMesaService.crearSesion(mesaValida.getCodigo());

            Map<String, Object> response = new HashMap<>();
            response.put("mesa", mesaValida.getNombre());
            response.put("codigoMesa", mesaValida.getCodigo());
            response.put("token", sesion.getToken());
            response.put("sucursalId", mesaValida.getSucursalId());
            response.put("tasaImpuesto", tasaImpuesto);
            sucursalRepository.findById(mesaValida.getSucursalId())
                    .ifPresent(sucursal -> response.put("sucursalNombre", sucursal.getNombre()));
            response.put("menu", menuService
                    .getMenuPorSucursal(mesaValida.getSucursalId())
                    .get("categorias"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

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
            response.put("tasaImpuesto", tasaImpuesto);

            List<Sucursal> sucursales = sucursalRepository
                    .findByRestauranteIdAndActivaTrue(restaurante.getId());
            List<Map<String, Object>> sucursalesResponse = new ArrayList<>();
            for (Sucursal sucursal : sucursales) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", sucursal.getId());
                item.put("nombre", sucursal.getNombre());
                item.put("direccion", sucursal.getDireccion());
                item.put("telefono", sucursal.getTelefono());
                sucursalesResponse.add(item);
            }
            response.put("sucursales", sucursalesResponse);
            response.put("sucursalId", sucursales.size() == 1 ? sucursales.get(0).getId() : null);
            response.put("menu", menuService.getMenuPublicoPorRestaurante(restaurante.getId()).get("categorias"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

    @GetMapping("/menu/por-sesion")
    public ResponseEntity<?> menuPorSesion(@RequestParam String token) {

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
            response.put("sucursalId", mesaOpt.get().getSucursalId());
            response.put("tasaImpuesto", tasaImpuesto);
            sucursalRepository.findById(mesaOpt.get().getSucursalId())
                    .ifPresent(sucursal -> response.put("sucursalNombre", sucursal.getNombre()));
            response.put("menu", menuService
                    .getMenuPorSucursal(mesaOpt.get().getSucursalId())
                    .get("categorias"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No fue posible cargar el menú. Intente nuevamente."));
        }
    }

    @PutMapping("/mesa/sesion/tipo-servicio")
    public ResponseEntity<?> registrarTipoServicio(@RequestParam String token,
                                                   @RequestParam(required = false) String tipo) {
        try {
            String registrado = sesionMesaService.registrarTipoServicio(token, tipo);
            return ResponseEntity.ok(Map.of("tipoServicio", registrado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "SESION_EXPIRADA",
                    "mensaje", e.getMessage()
            ));
        }
    }

    @GetMapping("/mesa/sesion/tipo-servicio")
    public ResponseEntity<?> obtenerTipoServicio(@RequestParam String token) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoServicio", sesionMesaService.obtenerTipoServicio(token).orElse(null));
        return ResponseEntity.ok(datos);
    }

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

    @PostMapping("/mesa/sesion/cerrar")
    public ResponseEntity<?> cerrarSesionMesa(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        sesionMesaService.cerrarSesion(token);
        return ResponseEntity.ok("Sesión cerrada");
    }

}
