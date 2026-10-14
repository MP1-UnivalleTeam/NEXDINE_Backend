package com.restaurant.app.services;

import com.restaurant.app.model.Producto;
import com.restaurant.app.services.CloudinaryStorageService.CloudinaryStorageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Service
public class ProductoImagenService {

    private static final Logger log = LoggerFactory.getLogger(ProductoImagenService.class);

    private final ProductoService productoService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final ImagenProductoValidador validador;

    public ProductoImagenService(ProductoService productoService,
                                 CloudinaryStorageService cloudinaryStorageService,
                                 ImagenProductoValidador validador) {
        this.productoService = productoService;
        this.cloudinaryStorageService = cloudinaryStorageService;
        this.validador = validador;
    }

    public boolean cloudinaryDisponible() {
        return cloudinaryStorageService.estaDisponible();
    }

    public List<String> variablesFaltantes() {
        return cloudinaryStorageService.variablesFaltantes();
    }

    public long getMaxBytes() {
        return validador.getMaxBytes();
    }

    public Producto crearConImagen(String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   MultipartFile archivo,
                                   String imagenUrl,
                                   UUID restauranteId,
                                   List<UUID> sucursalIds) {

        if (archivo == null || archivo.isEmpty()) {
            return productoService.createProducto(
                    nombre, descripcion, precio, imagenUrl, restauranteId, sucursalIds);
        }

        validador.validar(archivo);

        CloudinaryStorageService.ResultadoSubida subida =
                cloudinaryStorageService.subirImagen(archivo);

        Producto creado;
        try {
            creado = productoService.createProducto(
                    nombre, descripcion, precio, subida.url(), restauranteId, sucursalIds);
        } catch (RuntimeException e) {
            log.warn("NEXDINE | No se pudo crear el producto; se elimina el recurso subido.");
            eliminarSiEsSeguro(subida.publicId(), null);
            throw e;
        }

        productoService.asignarImagen(creado.getId(), subida.url(), subida.publicId());
        creado.setImagenPublicId(subida.publicId());

        return creado;
    }

    public Producto actualizarConImagen(@NonNull UUID id,
                                        String nombre,
                                        String descripcion,
                                        BigDecimal precio,
                                        MultipartFile archivo,
                                        String imagenUrl,
                                        UUID categoriaId,
                                        UUID restauranteId,
                                        List<UUID> sucursalIds) {

        Producto actual = productoService.getProductoById(id);
        if (actual == null) {
            throw new ProductoService.ProductoNoEncontradoException("El producto no existe.");
        }

        String publicIdAnterior = actual.getImagenPublicId();

        boolean hayArchivo = archivo != null && !archivo.isEmpty();
        boolean hayUrl = imagenUrl != null && !imagenUrl.isBlank();

        if (!hayArchivo && !hayUrl) {
            productoService.updateProducto(
                    id, nombre, descripcion, precio, null, null,
                    categoriaId, restauranteId, sucursalIds);

            return productoService.getProductoById(id);
        }

        if (!hayArchivo) {
            productoService.updateProducto(
                    id, nombre, descripcion, precio, imagenUrl, null,
                    categoriaId, restauranteId, sucursalIds);

            productoService.asignarImagen(id, imagenUrl, null);

            if (publicIdAnterior != null) {
                eliminarSiEsSeguro(publicIdAnterior, id);
            }

            return productoService.getProductoById(id);
        }

        validador.validar(archivo);

        CloudinaryStorageService.ResultadoSubida subida;
        try {
            subida = cloudinaryStorageService.subirImagen(archivo);
        } catch (CloudinaryStorageException e) {
            log.warn("NEXDINE | Fallo al subir la nueva imagen del producto {}; "
                    + "se conserva su imagen actual.", id);
            throw e;
        }

        try {
            productoService.updateProducto(
                    id, nombre, descripcion, precio, subida.url(), subida.publicId(),
                    categoriaId, restauranteId, sucursalIds);
        } catch (RuntimeException e) {
            log.warn("NEXDINE | No se pudo actualizar el producto {}; "
                    + "se elimina el recurso nuevo subido.", id);
            eliminarSiEsSeguro(subida.publicId(), null);
            throw e;
        }

        productoService.asignarImagen(id, subida.url(), subida.publicId());

        if (publicIdAnterior != null && !publicIdAnterior.equals(subida.publicId())) {
            eliminarSiEsSeguro(publicIdAnterior, id);
        }

        return productoService.getProductoById(id);
    }

    public void eliminarProductoYLimpiarImagen(@NonNull UUID id) {
        Producto producto = productoService.getProductoById(id);
        String publicId = producto != null ? producto.getImagenPublicId() : null;

        productoService.eliminarProducto(id);

        eliminarSiEsSeguro(publicId, id);
    }

    private void eliminarSiEsSeguro(String publicId, UUID excludeId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }

        if (!cloudinaryStorageService.esRecursoPropio(publicId)) {
            log.debug("NEXDINE | Recurso externo (no gestionado por el sistema); no se elimina.");
            return;
        }

        if (!productoService.imagenPublicIdSinOtrosUsos(publicId, excludeId)) {
            log.warn("NEXDINE | El recurso {} todavía es usado por otro producto; no se elimina.",
                    publicId);
            return;
        }

        cloudinaryStorageService.eliminarImagen(publicId);
    }
}
