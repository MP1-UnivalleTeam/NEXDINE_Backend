package com.restaurant.app.controller;

import com.restaurant.app.model.Producto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.CategoriaService;
import com.restaurant.app.services.CloudinaryStorageService;
import com.restaurant.app.services.ImagenProductoValidador;
import com.restaurant.app.services.ProductoImagenService;
import com.restaurant.app.services.ProductoService;
import com.restaurant.app.services.SkuService;
import com.restaurant.app.services.SkuUtil;
import com.restaurant.app.services.UsuarioContextService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoImagenService productoImagenService;
    private final SkuService skuService;
    private final ProductoRepository productoRepository;
    private final UsuarioContextService usuarioContextService;
    private final SucursalRepository sucursalRepository;
    private final CategoriaService categoriaService;

    public ProductoController(ProductoService productoService,
                              ProductoImagenService productoImagenService,
                              SkuService skuService,
                              ProductoRepository productoRepository,
                              UsuarioContextService usuarioContextService,
                              SucursalRepository sucursalRepository,
                              CategoriaService categoriaService) {
        this.productoService = productoService;
        this.productoImagenService = productoImagenService;
        this.skuService = skuService;
        this.productoRepository = productoRepository;
        this.usuarioContextService = usuarioContextService;
        this.sucursalRepository = sucursalRepository;
        this.categoriaService = categoriaService;
    }

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

            List<Producto> productos = productoService.listarParaContexto(
                    contexto.getRestauranteId(), contexto.getSucursalId());

            return ResponseEntity.ok(productos);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/disponibilidad")
    public ResponseEntity<?> actualizarDisponibilidad(
            @PathVariable @NonNull UUID id,
            @RequestParam boolean disponible,
            @RequestParam(name = "sucursalId", required = false) UUID sucursalId,
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
            @RequestParam("sucursalIds") List<UUID> sucursalIds,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede agregar productos al catálogo."));
        }

        ResponseEntity<?> error = validarDatosProducto(nombre, precio, sucursalIds);
        if (error != null) {
            return error;
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.createProducto(
                    nombre.trim(),
                    descripcion,
                    precio,
                    normalizarImagen(imagen),
                    contexto.getRestauranteId(),
                    sucursalIds
            );

            return ResponseEntity.ok(producto);

        } catch (ProductoService.ProductoDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al guardar el producto: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> agregarProductoConImagen(
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(name = "imagen", required = false) MultipartFile imagenArchivo,
            @RequestParam(name = "imagenUrl", required = false) String imagenUrl,
            @RequestParam("sucursalIds") List<UUID> sucursalIds,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede agregar productos al catálogo."));
        }

        ResponseEntity<?> error = validarDatosProducto(nombre, precio, sucursalIds);
        if (error != null) {
            return error;
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoImagenService.crearConImagen(
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagenArchivo,
                    normalizarImagen(imagenUrl),
                    contexto.getRestauranteId(),
                    sucursalIds
            );

            return ResponseEntity.ok(producto);

        } catch (ProductoService.ProductoDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (ImagenProductoValidador.ImagenDemasiadoGrandeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", e.getMessage()));
        } catch (ImagenProductoValidador.ImagenInvalidaException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (CloudinaryStorageService.CloudinaryNoConfiguradoException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage(),
                            "variablesFaltantes", e.getVariablesFaltantes()));
        } catch (CloudinaryStorageService.CloudinaryCommunicationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
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
            @PathVariable @NonNull UUID id,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(required = false) String imagen,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(name = "sucursalIds", required = false) List<UUID> sucursalIds,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede modificar el catálogo de productos."));
        }

        ResponseEntity<?> error = validarDatosProducto(nombre, precio, null);
        if (error != null) {
            return error;
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.getProductoById(id);
            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

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

        } catch (ProductoService.ProductoDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar el producto: " + e.getMessage()));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarProductoConImagen(
            @PathVariable @NonNull UUID id,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            @RequestParam(name = "imagen", required = false) MultipartFile imagenArchivo,
            @RequestParam(name = "imagenUrl", required = false) String imagenUrl,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(name = "sucursalIds", required = false) List<UUID> sucursalIds,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede modificar el catálogo de productos."));
        }

        ResponseEntity<?> error = validarDatosProducto(nombre, precio, null);
        if (error != null) {
            return error;
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.getProductoById(id);
            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

            if (!producto.getRestauranteId().equals(contexto.getRestauranteId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "No tiene permisos para modificar este producto"));
            }

            Producto productoActualizado = productoImagenService.actualizarConImagen(
                    id,
                    nombre.trim(),
                    descripcion,
                    precio,
                    imagenArchivo,
                    imagenUrl,
                    categoriaId,
                    contexto.getRestauranteId(),
                    sucursalIds
            );

            return ResponseEntity.ok(productoActualizado);

        } catch (ProductoService.ProductoDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (ImagenProductoValidador.ImagenDemasiadoGrandeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", e.getMessage()));
        } catch (ImagenProductoValidador.ImagenInvalidaException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (CloudinaryStorageService.CloudinaryNoConfiguradoException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage(),
                            "variablesFaltantes", e.getVariablesFaltantes()));
        } catch (CloudinaryStorageService.CloudinaryCommunicationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (ProductoService.ProductoNoEncontradoException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al actualizar el producto: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/sucursales")
    public ResponseEntity<?> sucursalesDeProducto(@PathVariable @NonNull UUID id, HttpSession session) {
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
            @PathVariable @NonNull UUID id,
            HttpSession session) {

        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        String rol = usuarioActual.getRol();
        if (!"ADMINISTRADOR".equals(rol) && !"SUPERADMIN".equals(rol)) {
            return ResponseEntity.status(403).build();
        }

        try {
            UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuarioActual);

            Producto producto = productoService.getProductoById(id);

            if (producto == null) {
                return ResponseEntity.notFound().build();
            }

            if (!producto.getRestauranteId().equals(contexto.getRestauranteId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "No tiene permisos para eliminar este producto"));
            }

            productoImagenService.eliminarProductoYLimpiarImagen(id);

            return ResponseEntity.ok(Map.of("message", "Producto eliminado exitosamente"));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al eliminar el producto: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> validarDatosProducto(String nombre, BigDecimal precio, List<UUID> sucursalIds) {
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

        if (sucursalIds != null && sucursalIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Debe seleccionar al menos una sucursal"));
        }

        return null;
    }

    private String normalizarImagen(String imagen) {
        if (imagen == null || imagen.isBlank()) {
            return null;
        }
        return imagen.trim();
    }

    @GetMapping("/imagenes/config")
    public ResponseEntity<?> configuracionImagenes(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"ADMINISTRADOR".equals(usuarioActual.getRol()) && !"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cloudinaryDisponible", productoImagenService.cloudinaryDisponible());
        info.put("tamanoMaximoBytes", productoImagenService.getMaxBytes());
        info.put("formatos", List.of("JPEG", "PNG", "WebP"));

        if (!productoImagenService.cloudinaryDisponible()) {
            info.put("variablesFaltantes", productoImagenService.variablesFaltantes());
        }

        return ResponseEntity.ok(info);
    }

    @GetMapping("/sku/estado")
    public ResponseEntity<?> estadoSku(HttpSession session) {
        Usuario usuarioActual = (Usuario) session.getAttribute("currentUser");

        if (usuarioActual == null) {
            return ResponseEntity.status(401).build();
        }

        if (!"SUPERADMIN".equals(usuarioActual.getRol())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Solo el SUPERADMIN puede consultar el estado del SKU."));
        }

        try {
            SkuService.EstadoSku estado = skuService.consultarEstado();

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("restaurantesSinCodigo", estado.restaurantesSinCodigo());
            info.put("restaurantesSinIniciales", estado.restaurantesSinIniciales());
            info.put("migracionCompleta", estado.completo());
            info.put("productosSinSku", productoRepository.countBySkuIsNull());
            info.put("formato", SkuUtil.INICIALES_POR_DEFECTO + "-"
                    + SkuUtil.PREFIJO_CODIGO + "000-000000");

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al consultar el estado del SKU: " + e.getMessage()));
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException e) {
        long maximo = productoImagenService.getMaxBytes();
        double mb = maximo / (1024.0 * 1024.0);

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "La imagen supera el tamaño máximo permitido de "
                        + String.format(java.util.Locale.ROOT, "%.0f", mb) + " MB."));
    }
}
