package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.ProductoService;
import com.restaurant.app.services.UsuarioContextService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private UsuarioContextService usuarioContextService;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private com.restaurant.app.services.CategoriaService categoriaService;

    /** Categorías activas del restaurante: alimenta el select del formulario. */
@GetMapping("/categorias")
    public ResponseEntity<?> listarCategorias(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            return ResponseEntity.ok(categoriaService.listarActivas(usuarioActual));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Sucursales del restaurante del usuario autenticado.
     * Alimenta el checklist de sucursales del formulario de producto.
     */
@GetMapping("/sucursales")
    public ResponseEntity<?> listarSucursales(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);
            return ResponseEntity.ok(sucursalRepository.findByRestauranteId(contexto.getRestauranteId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listarProductos(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            // ADMIN: solo productos de su sucursal, con su disponibilidad real.
            // SUPERADMIN: todo el restaurante, disponibilidad por sucursal=null.
            List<Producto> productos = productoService.listarParaContexto(
                    contexto.getRestauranteId(), contexto.getSucursalId());

            return ResponseEntity.ok(productos);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * RF007 — Actualiza la disponibilidad de un producto en una sucursal.
     *
     * Para ADMINISTRADOR la sucursal la determina el backend (la suya);
     * el sucursalId recibido se ignora. Para SUPERADMIN es obligatorio
     * indicar explícitamente la sucursal.
     */
    @PutMapping("/{id}/disponibilidad")
    public ResponseEntity<?> actualizarDisponibilidad(
            @PathVariable Long id,
            @RequestParam boolean disponible,
            @RequestParam(name = "sucursalId", required = false) Long sucursalId,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        boolean esSuperadmin = "SUPERADMIN".equals(usuarioActual.getRol());
        boolean esAdmin = "ADMINISTRADOR".equals(usuarioActual.getRol());

        if (!esSuperadmin && !esAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "No tiene permisos para modificar disponibilidad."));
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.actualizarDisponibilidad(
                    id,
                    disponible,
                    contexto.getRestauranteId(),
                    contexto.getSucursalId(),
                    esSuperadmin,
                    sucursalId
            );

            return ResponseEntity.ok(producto);

        } catch (ProductoService.ProductoNoEncontradoException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (ProductoService.ProductoNoAsociadoException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (ProductoService.SinPermisoException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar la disponibilidad: " + e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> agregarProducto(
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(required = false) String imagen,
            @RequestParam("sucursalIds") List<Long> sucursalIds,
            HttpSession session) {

        // Verificar autenticación
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        // RF012: solo SUPERADMIN crea productos del catálogo global
        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede agregar productos al catálogo."));
        }

        // Validar campos obligatorios
        if (nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre del producto es obligatorio"));
        }

        if (precio == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio del producto es obligatorio"));
        }

        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio debe ser mayor que 0"));
        }

        if (sucursalIds == null || sucursalIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Debe seleccionar al menos una sucursal"));
        }

        try {
            // El restaurante se obtiene del contexto autenticado, nunca del frontend
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            // Crear UN producto global + una relación por sucursal
            Producto producto = productoService.createProducto(
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagen,
                    contexto.getRestauranteId(),
                    sucursalIds
            );

            return ResponseEntity.ok(producto);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al guardar el producto: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarProducto(
            @PathVariable Long id,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(required = false) String imagen,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(name = "sucursalIds", required = false) List<Long> sucursalIds,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        // RF013: la modificación del catálogo global es del SUPERADMIN
        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede modificar el catálogo de productos."));
        }

        if (nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El nombre del producto es obligatorio"));
        }

        if (precio == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio del producto es obligatorio"));
        }

        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio debe ser mayor que 0"));
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.getProductoById(id);
            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

            // Aislamiento por restaurante
            if (!producto.getRestauranteId().equals(contexto.getRestauranteId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "No tiene permisos para modificar este producto"));
            }

            Producto productoActualizado = productoService.updateProducto(
                    id,
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagen,
                    categoriaId,
                    contexto.getRestauranteId(),
                    sucursalIds
            );

            return ResponseEntity.ok(productoActualizado);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar el producto: " + e.getMessage()));
        }
    }

    /**
     * Sucursales donde un producto está actualmente asociado.
     * Alimenta el checklist del modal de edición.
     */
    @GetMapping("/{id}/sucursales")
    public ResponseEntity<?> sucursalesDeProducto(@PathVariable Long id, HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.getProductoById(id);
            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

            if (!producto.getRestauranteId().equals(contexto.getRestauranteId())) {
                return ResponseEntity.status(403).build();
            }

            return ResponseEntity.ok(productoService.listarSucursalesDeProducto(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarProducto(
            @PathVariable Long id,
            HttpSession session) {

        // Verificar autenticación
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        // Verificar rol ADMINISTRADOR o SUPERADMIN
        String rol = usuarioActual.getRol();
        if (!"ADMINISTRADOR".equals(rol) && !"SUPERADMIN".equals(rol)) {
            return ResponseEntity.status(403).build();
        }

        try {
            // Obtener el contexto del usuario (restaurante y sucursal)
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            // Buscar el producto
            Producto producto = productoService.getProductoById(id);

            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

            // Verificar que el producto pertenece al restaurante del administrador
            if (!producto.getRestauranteId().equals(contexto.getRestauranteId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "No tiene permisos para eliminar este producto"));
            }

            // Eliminar el producto (y sus asociaciones en producto_sucursal)
            productoService.eliminarProducto(id);

            return ResponseEntity.ok(Map.of("message", "Producto eliminado exitosamente"));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al eliminar el producto: " + e.getMessage()));
        }
    }
}
