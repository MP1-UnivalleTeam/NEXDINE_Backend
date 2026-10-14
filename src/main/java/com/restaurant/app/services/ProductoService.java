package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoSucursalRepository productoSucursalRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;
    private final SkuService skuService;

    public ProductoService(ProductoRepository productoRepository,
                           ProductoSucursalRepository productoSucursalRepository,
                           CategoriaRepository categoriaRepository,
                           SucursalRepository sucursalRepository,
                           SkuService skuService) {
        this.productoRepository = productoRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.categoriaRepository = categoriaRepository;
        this.sucursalRepository = sucursalRepository;
        this.skuService = skuService;
    }

    public List<Producto> getAllProductos() {
        return productoRepository.findAll();
    }

    public List<Producto> listarParaContexto(UUID restauranteId, UUID sucursalId) {
        List<Producto> productos = productoRepository.findByRestauranteId(restauranteId);

        if (sucursalId == null) {
            productos.forEach(p -> p.setDisponibleEnMiSucursal(null));
            return productos;
        }

        for (Producto producto : productos) {
            Optional<ProductoSucursal> relacion = productoSucursalRepository
                    .findByProductoIdAndSucursalId(producto.getId(), sucursalId);

            producto.setDisponibleEnMiSucursal(
                    relacion.map(ps -> ps.isDisponible()).orElse(null));
        }

        return productos;
    }

    public Producto getProductoById(@NonNull UUID id) {
        return productoRepository.findById(id).orElse(null);
    }

    public boolean imagenPublicIdSinOtrosUsos(String imagenPublicId, UUID excludeId) {
        if (imagenPublicId == null || imagenPublicId.isBlank()) {
            return true;
        }

        if (excludeId == null) {
            return productoRepository.countByImagenPublicId(imagenPublicId) == 0;
        }

        return productoRepository.countByImagenPublicIdAndIdNot(imagenPublicId, excludeId) == 0;
    }

    @Transactional
    @NonNull
    public Producto createProducto(String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   UUID restauranteId,
                                   List<UUID> sucursalIds) {

        if (sucursalIds == null || sucursalIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        List<UUID> sucursalIdsUnicas = sucursalIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (sucursalIdsUnicas.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        for (UUID sucursalId : sucursalIdsUnicas) {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La sucursal seleccionada no existe."));

            if (!sucursal.getRestauranteId().equals(restauranteId)) {
                throw new IllegalArgumentException(
                        "Una de las sucursales seleccionadas no pertenece a su restaurante.");
            }

            if (!sucursal.isActiva()) {
                throw new IllegalArgumentException(
                        "La sucursal \"" + sucursal.getNombre() + "\" está inactiva.");
            }
        }

        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre(nombre);
        nuevoProducto.setDescripcion(descripcion);
        nuevoProducto.setPrecio(precio);
        nuevoProducto.setImagen(imagen);
        nuevoProducto.setDisponible(true);
        nuevoProducto.setRestauranteId(restauranteId);

        nuevoProducto.setNombreNormalizado(SkuUtil.normalizarNombre(nombre));

        nuevoProducto.setSku(skuService.generarSku(restauranteId));

        verificarNombreDuplicado(restauranteId, nuevoProducto.getNombreNormalizado(), null, nombre);

        Producto productoGuardado;
        try {
            productoGuardado = productoRepository.saveAndFlush(nuevoProducto);
        } catch (DataIntegrityViolationException e) {
            if (esViolacionDeNombreDuplicado(e)) {
                throw new ProductoDuplicadoException(nombre);
            }
            throw e;
        }

        List<ProductoSucursal> relaciones = sucursalIdsUnicas.stream()
                .map(sucursalId -> new ProductoSucursal(
                        productoGuardado.getId(), sucursalId, restauranteId))
                .peek(ps -> ps.setDisponible(true))
                .toList();

        productoSucursalRepository.saveAll(relaciones);

        return productoGuardado;
    }

    @Transactional
    public Producto updateProducto(UUID id,
                                   String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   UUID categoriaId,
                                   UUID restauranteId,
                                   List<UUID> sucursalIds) {

        return updateProducto(id, nombre, descripcion, precio, imagen, null, categoriaId,
                restauranteId, sucursalIds);
    }

    @Transactional
    public Producto updateProducto(UUID id,
                                   String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   String imagenPublicId,
                                   UUID categoriaId,
                                   UUID restauranteId,
                                   List<UUID> sucursalIds) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe."));

        if (restauranteId != null && !producto.getRestauranteId().equals(restauranteId)) {
            throw new IllegalStateException("El producto no pertenece a su restaurante.");
        }

        if (categoriaId != null) {
            Categoria categoria = categoriaRepository.findById(categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría no existe."));

            if (producto.getRestauranteId() != null
                    && !categoria.getRestauranteId().equals(producto.getRestauranteId())) {
                throw new IllegalArgumentException("La categoría no pertenece a su restaurante.");
            }
        }

        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecio(precio);

        String nombreNormalizado = SkuUtil.normalizarNombre(nombre);
        verificarNombreDuplicado(restauranteId, nombreNormalizado, id, nombre);

        producto.setNombreNormalizado(nombreNormalizado);

        if (producto.getSku() == null || producto.getSku().isBlank()) {
            producto.setSku(skuService.generarSku(producto.getRestauranteId()));
        }

        if (imagen != null) {
            producto.setImagen(imagen.isBlank() ? null : imagen);

            producto.setImagenPublicId(
                    imagen.isBlank() ? null
                            : (imagenPublicId != null ? imagenPublicId : producto.getImagenPublicId()));
        }

        producto.setCategoriaId(categoriaId);

        Producto guardado;
        try {
            guardado = productoRepository.saveAndFlush(producto);
        } catch (DataIntegrityViolationException e) {
            throw new ProductoDuplicadoException(nombre);
        }

        if (sucursalIds != null) {
            sincronizarSucursales(guardado.getId(), restauranteId, sucursalIds);
        }

        return guardado;
    }

    @Transactional
    public String asignarImagen(UUID productoId, String url, String imagenPublicId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe."));

        String publicIdAnterior = producto.getImagenPublicId();

        producto.setImagen(url);
        producto.setImagenPublicId(imagenPublicId);
        productoRepository.save(producto);

        return publicIdAnterior;
    }

    private void sincronizarSucursales(UUID productoId, UUID restauranteId, List<UUID> sucursalIds) {
        List<UUID> deseadas = sucursalIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (deseadas.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        for (UUID sucursalId : deseadas) {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La sucursal seleccionada no existe."));

            if (restauranteId != null && !sucursal.getRestauranteId().equals(restauranteId)) {
                throw new IllegalArgumentException(
                        "Una de las sucursales seleccionadas no pertenece a su restaurante.");
            }
        }

        List<ProductoSucursal> actuales = productoSucursalRepository.findByProductoId(productoId);

        for (UUID sucursalId : deseadas) {
            boolean existe = actuales.stream()
                    .anyMatch(ps -> ps.getSucursalId().equals(sucursalId));
            if (!existe) {
                ProductoSucursal ps = new ProductoSucursal(
                        productoId, sucursalId, restauranteId);
                ps.setDisponible(true);
                productoSucursalRepository.save(ps);
            }
        }

        List<ProductoSucursal> aEliminar = actuales.stream()
                .filter(ps -> !deseadas.contains(ps.getSucursalId()))
                .toList();

        if (!aEliminar.isEmpty()) {
            productoSucursalRepository.deleteAll(aEliminar);
        }
    }

    public List<UUID> listarSucursalesDeProducto(UUID productoId) {
        return productoSucursalRepository.findByProductoId(productoId).stream()
                .map(ps -> ps.getSucursalId())
                .toList();
    }

    @Transactional
    public Producto actualizarDisponibilidad(@NonNull UUID productoId,
                                             boolean disponible,
                                             UUID restauranteId,
                                             UUID sucursalIdAdmin,
                                             boolean esSuperadmin,
                                             UUID sucursalIdSolicitada) {

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException("El producto no existe."));

        if (restauranteId != null && !producto.getRestauranteId().equals(restauranteId)) {
            throw new SinPermisoException("El producto pertenece a otro restaurante.");
        }

        UUID sucursalObjetivo;
        if (esSuperadmin) {
            sucursalObjetivo = sucursalIdSolicitada;
            if (sucursalObjetivo == null) {
                throw new IllegalArgumentException(
                        "Debe seleccionar la sucursal sobre la que desea cambiar la disponibilidad.");
            }

            Sucursal sucursal = sucursalRepository.findById(sucursalObjetivo)
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe."));
            if (!sucursal.getRestauranteId().equals(restauranteId)) {
                throw new SinPermisoException("La sucursal no pertenece a su restaurante.");
            }
        } else {
            sucursalObjetivo = sucursalIdAdmin;
            if (sucursalObjetivo == null) {
                throw new SinPermisoException(
                        "El administrador no tiene una sucursal asignada.");
            }
        }

        ProductoSucursal relacion = productoSucursalRepository
                .findByProductoIdAndSucursalId(productoId, sucursalObjetivo)
                .orElseThrow(() -> new ProductoNoAsociadoException(
                        "El producto no está asociado a su sucursal. "
                        + "La asociación se realiza al crear o editar el producto."));

        relacion.setDisponible(disponible);
        productoSucursalRepository.save(relacion);

        producto.setDisponibleEnMiSucursal(disponible);

        return producto;
    }

    public static class ProductoNoEncontradoException extends RuntimeException {
        public ProductoNoEncontradoException(String message) {
            super(message);
        }
    }

    public static class ProductoNoAsociadoException extends RuntimeException {
        public ProductoNoAsociadoException(String message) {
            super(message);
        }
    }

    public static class SinPermisoException extends RuntimeException {
        public SinPermisoException(String message) {
            super(message);
        }
    }

    public static class ProductoDuplicadoException extends RuntimeException {
        private final String nombre;

        public ProductoDuplicadoException(String nombre) {
            super("Ya existe un producto con el nombre \"" + nombre
                    + "\" en este restaurante. Modifique el existente o use un nombre diferente.");
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }
    }

    private boolean esViolacionDeNombreDuplicado(DataIntegrityViolationException e) {
        String mensaje = e.getMostSpecificCause().getMessage();

        if (mensaje == null) {
            return false;
        }

        String minusculas = mensaje.toLowerCase(java.util.Locale.ROOT);

        return minusculas.contains("uq_productos_restaurante_nombre_norm")
                || (minusculas.contains("duplicate key")
                    && minusculas.contains("nombre_normalizado"));
    }

    private void verificarNombreDuplicado(UUID restauranteId,
                                          String nombreNormalizado,
                                          UUID excludeId,
                                          String nombreOriginal) {
        if (nombreNormalizado == null) {
            return;
        }

        boolean duplicado = (excludeId == null)
                ? productoRepository.existsByRestauranteIdAndNombreNormalizado(
                        restauranteId, nombreNormalizado)
                : productoRepository.existsByRestauranteIdAndNombreNormalizadoAndIdNot(
                        restauranteId, nombreNormalizado, excludeId);

        if (duplicado) {
            throw new ProductoDuplicadoException(nombreOriginal);
        }
    }

    public boolean validarCategoriaPorRestaurante(@NonNull UUID categoriaId, UUID restauranteId) {
        Optional<Categoria> categoria = categoriaRepository.findById(categoriaId);
        return categoria.isPresent() && categoria.get().getRestauranteId().equals(restauranteId);
    }

    @Transactional
    public void eliminarProducto(@NonNull UUID id) {
        eliminarProductoConImagen(id);
    }

    @Transactional
    public String eliminarProductoConImagen(@NonNull UUID id) {
        String publicIdAnterior = productoRepository.findById(id)
                .map(producto -> producto.getImagenPublicId())
                .orElse(null);

        List<ProductoSucursal> asociaciones = productoSucursalRepository.findByProductoId(id);
        productoSucursalRepository.deleteAll(asociaciones);

        productoRepository.deleteById(id);

        return publicIdAnterior;
    }
}
