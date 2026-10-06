package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
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

    @GetMapping
    public ResponseEntity<?> listarProductos(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        List<Producto> productos = productoService.getAllProductos();
        return ResponseEntity.ok(productos);
    }

    @PostMapping("/create")
    public ResponseEntity<?> agregarProducto(
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(required = false) String imagen,
            HttpSession session) {

        // Verificar autenticación
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        // Verificar rol ADMINISTRADOR
        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
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

        // Validar precio no negativo
        if (precio.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio no puede ser negativo"));
        }

        try {
            // Obtener el contexto del usuario (restaurante y sucursal)
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            // Crear el producto con el restaurante del administrador
            Producto producto = productoService.createProducto(
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagen,
                    contexto.getRestauranteId(),
                    contexto.getSucursalId()
            );

            return ResponseEntity.ok(producto);

        } catch (IllegalStateException e) {
            // Error de configuración: administrador sin sucursal
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
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
            HttpSession session) {

        // Verificar autenticación
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        // Verificar rol ADMINISTRADOR
        if (!"ADMINISTRADOR".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
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

        // Validar precio no negativo
        if (precio.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El precio no puede ser negativo"));
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
                        .body(Map.of("error", "No tiene permisos para modificar este producto"));
            }

            // Validar que la categoría pertenece al mismo restaurante (si se proporciona)
            if (categoriaId != null) {
                boolean categoriaValida = productoService.validarCategoriaPorRestaurante(categoriaId, contexto.getRestauranteId());
                if (!categoriaValida) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "La categoría no pertenece a su restaurante"));
                }
            }

            // Actualizar el producto
            Producto productoActualizado = productoService.updateProducto(
                    id,
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagen,
                    categoriaId
            );

            return ResponseEntity.ok(productoActualizado);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar el producto: " + e.getMessage()));
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
