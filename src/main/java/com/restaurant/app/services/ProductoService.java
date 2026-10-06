package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoSucursalRepository productoSucursalRepository;
    private final CategoriaRepository categoriaRepository;
    private final SucursalRepository sucursalRepository;

    public ProductoService(ProductoRepository productoRepository,
                           ProductoSucursalRepository productoSucursalRepository,
                           CategoriaRepository categoriaRepository,
                           SucursalRepository sucursalRepository) {
        this.productoRepository = productoRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.categoriaRepository = categoriaRepository;
        this.sucursalRepository = sucursalRepository;
    }

    public List<Producto> getAllProductos() {
        return productoRepository.findAll();
    }

    /**
     * Lista los productos del restaurante con la disponibilidad resuelta
     * para la sucursal del usuario (RF007).
     *
     * ADMINISTRADOR → solo productos asociados a SU sucursal.
     * SUPERADMIN     → todos los del restaurante (disponibleEnMiSucursal = null).
     */
    public List<Producto> listarParaContexto(Long restauranteId, Long sucursalId) {
        List<Producto> productos = productoRepository.findByRestauranteId(restauranteId);

        if (sucursalId == null) {
            // SUPERADMIN: sin sucursal de trabajo
            productos.forEach(p -> p.setDisponibleEnMiSucursal(null));
            return productos;
        }

        for (Producto producto : productos) {
            Optional<ProductoSucursal> relacion = productoSucursalRepository
                    .findByProductoIdAndSucursalId(producto.getId(), sucursalId);

            // null = no está asociado a la sucursal del usuario
            producto.setDisponibleEnMiSucursal(
                    relacion.map(ProductoSucursal::isDisponible).orElse(null));
        }

        return productos;
    }

    public Producto getProductoById(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    /**
     * Crea UN producto global al restaurante y lo relaciona con las sucursales
     * seleccionadas mediante producto_sucursal.
     *
     * El restaurante NUNCA se toma del frontend: llega desde el contexto autenticado.
     * Todas las sucursales se validan contra ese restaurante antes de persistir.
     *
     * Transaccional: si falla alguna relación, se revierte todo (no quedan productos huérfanos).
     */
    @Transactional
    public Producto createProducto(String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   Long restauranteId,
                                   List<Long> sucursalIds) {

        // Validar que se seleccionó al menos una sucursal
        if (sucursalIds == null || sucursalIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        // Eliminar duplicados conservando el orden
        List<Long> sucursalIdsUnicas = sucursalIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (sucursalIdsUnicas.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        // Validar que todas las sucursales existen y pertenecen al restaurante
        for (Long sucursalId : sucursalIdsUnicas) {
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

        // Paso 1: crear UN SOLO producto en el catálogo global
        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre(nombre);
        nuevoProducto.setDescripcion(descripcion);
        nuevoProducto.setPrecio(precio);
        nuevoProducto.setImagen(imagen);
        nuevoProducto.setDisponible(true);
        nuevoProducto.setRestauranteId(restauranteId);

        Producto productoGuardado = productoRepository.save(nuevoProducto);

        // Paso 2: una relación producto_sucursal por cada sucursal seleccionada
        List<ProductoSucursal> relaciones = sucursalIdsUnicas.stream()
                .map(sucursalId -> {
                    ProductoSucursal ps = new ProductoSucursal();
                    ps.setProductoId(productoGuardado.getId());
                    ps.setSucursalId(sucursalId);
                    ps.setDisponible(true);
                    return ps;
                })
                .toList();

        productoSucursalRepository.saveAll(relaciones);

        return productoGuardado;
    }

    /**
     * Actualiza un producto global y sincroniza sus sucursales.
     *
     * - No se crea un producto nuevo: el global se conserva siempre.
     * - Si sucursalIds es null → NO se tocan las relaciones existentes.
     * - Si viene lista → se sincroniza (altas y bajas),
     *   preservando la disponibilidad actual de las que permanecen.
     */
    @Transactional
    public Producto updateProducto(Long id,
                                   String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   Long categoriaId,
                                   Long restauranteId,
                                   List<Long> sucursalIds) {

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe."));

        // Aislamiento por restaurante: no se puede editar productos ajenos
        if (restauranteId != null && !producto.getRestauranteId().equals(restauranteId)) {
            throw new IllegalStateException("El producto no pertenece a su restaurante.");
        }

        // Validar categoría si se proporciona
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
        producto.setImagen(imagen);
        producto.setCategoriaId(categoriaId);
        // disponibles y restauranteId se preservan

        Producto guardado = productoRepository.save(producto);

        // Sincronizar sucursales solo si el frontend envió la lista
        if (sucursalIds != null) {
            sincronizarSucursales(guardado.getId(), restauranteId, sucursalIds);
        }

        return guardado;
    }

    /**
     * Sincroniza producto_sucursal con la lista enviada.
     * Las sucursales que permanecen conservan su disponibilidad actual.
     */
    private void sincronizarSucursales(Long productoId, Long restauranteId, List<Long> sucursalIds) {
        List<Long> deseadas = sucursalIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (deseadas.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una sucursal.");
        }

        // Validar todas las sucursales contra el restaurante
        for (Long sucursalId : deseadas) {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La sucursal seleccionada no existe."));

            if (restauranteId != null && !sucursal.getRestauranteId().equals(restauranteId)) {
                throw new IllegalArgumentException(
                        "Una de las sucursales seleccionadas no pertenece a su restaurante.");
            }
        }

        List<ProductoSucursal> actuales = productoSucursalRepository.findByProductoId(productoId);

        // Altas: no crear duplicados
        for (Long sucursalId : deseadas) {
            boolean existe = actuales.stream()
                    .anyMatch(ps -> ps.getSucursalId().equals(sucursalId));
            if (!existe) {
                ProductoSucursal ps = new ProductoSucursal();
                ps.setProductoId(productoId);
                ps.setSucursalId(sucursalId);
                ps.setDisponible(true);
                productoSucursalRepository.save(ps);
            }
        }

        // Bajas: eliminar únicamente las desmarcadas
        List<ProductoSucursal> aEliminar = actuales.stream()
                .filter(ps -> !deseadas.contains(ps.getSucursalId()))
                .toList();

        if (!aEliminar.isEmpty()) {
            productoSucursalRepository.deleteAll(aEliminar);
        }
    }

    /** Sucursales actuales donde está asociado un producto. */
    public List<Long> listarSucursalesDeProducto(Long productoId) {
        return productoSucursalRepository.findByProductoId(productoId).stream()
                .map(ProductoSucursal::getSucursalId)
                .toList();
    }

    /**
     * RF007 — Actualiza la disponibilidad de un producto EN UNA SUCURSAL.
     *
     * Reglas:
     *  · Solo se modifica producto_sucursal.disponible. NUNCA productos.disponible.
     *  · La sucursal la decide el backend:
     *      - ADMINISTRADOR → siempre la suya (ignora cualquier sucursalId recibido)
     *      - SUPERADMIN     → debe indicar explícitamente la sucursal
     *  · El producto debe pertenecer al restaurante del contexto.
     *  · La relación producto_sucursal debe existir: RF007 NO crea asociaciones.
     */
    @Transactional
    public Producto actualizarDisponibilidad(Long productoId,
                                             boolean disponible,
                                             Long restauranteId,
                                             Long sucursalIdAdmin,
                                             boolean esSuperadmin,
                                             Long sucursalIdSolicitada) {

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException("El producto no existe."));

        // Aislamiento por restaurante
        if (restauranteId != null && !producto.getRestauranteId().equals(restauranteId)) {
            throw new SinPermisoException("El producto pertenece a otro restaurante.");
        }

        // Determinar la sucursal objetivo
        Long sucursalObjetivo;
        if (esSuperadmin) {
            sucursalObjetivo = sucursalIdSolicitada;
            if (sucursalObjetivo == null) {
                throw new IllegalArgumentException(
                        "Debe seleccionar la sucursal sobre la que desea cambiar la disponibilidad.");
            }

            // La sucursal debe ser del restaurante del contexto
            Sucursal sucursal = sucursalRepository.findById(sucursalObjetivo)
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe."));
            if (!sucursal.getRestauranteId().equals(restauranteId)) {
                throw new SinPermisoException("La sucursal no pertenece a su restaurante.");
            }
        } else {
            // ADMINISTRADOR: se usa SIEMPRE su sucursal; se ignora lo enviado
            sucursalObjetivo = sucursalIdAdmin;
            if (sucursalObjetivo == null) {
                throw new SinPermisoException(
                        "El administrador no tiene una sucursal asignada.");
            }
        }

        // La relación debe existir: RF007 no crea asociaciones
        ProductoSucursal relacion = productoSucursalRepository
                .findByProductoIdAndSucursalId(productoId, sucursalObjetivo)
                .orElseThrow(() -> new ProductoNoAsociadoException(
                        "El producto no está asociado a su sucursal. "
                        + "La asociación se realiza al crear o editar el producto."));

        // Único cambio: producto_sucursal.disponible
        relacion.setDisponible(disponible);
        productoSucursalRepository.save(relacion);

        // Reflejar el valor en la respuesta (campo transitorio)
        producto.setDisponibleEnMiSucursal(disponible);

        // productos.disponible NO se toca
        return producto;
    }

    /** Excepción: el producto no existe. */
    public static class ProductoNoEncontradoException extends RuntimeException {
        public ProductoNoEncontradoException(String message) {
            super(message);
        }
    }

    /** Excepción: el producto existe pero no está asociado a la sucursal. */
    public static class ProductoNoAsociadoException extends RuntimeException {
        public ProductoNoAsociadoException(String message) {
            super(message);
        }
    }

    /** Excepción: falta de permisos. */
    public static class SinPermisoException extends RuntimeException {
        public SinPermisoException(String message) {
            super(message);
        }
    }

    /**
     * Valida que una categoría pertenezca al restaurante especificado.
     */
    public boolean validarCategoriaPorRestaurante(Long categoriaId, Long restauranteId) {
        Optional<Categoria> categoria = categoriaRepository.findById(categoriaId);
        return categoria.isPresent() && categoria.get().getRestauranteId().equals(restauranteId);
    }

    /**
     * Elimina un producto y sus asociaciones en producto_sucursal.
     * Transaccional para evitar registros huérfanos.
     */
    @Transactional
    public void eliminarProducto(Long id) {
        // Eliminar primero las asociaciones en producto_sucursal
        List<ProductoSucursal> asociaciones = productoSucursalRepository.findByProductoId(id);
        productoSucursalRepository.deleteAll(asociaciones);

        // Luego eliminar el producto
        productoRepository.deleteById(id);
    }
}
