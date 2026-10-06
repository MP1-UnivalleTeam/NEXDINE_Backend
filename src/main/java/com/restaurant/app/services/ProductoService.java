package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.repository.CategoriaRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;

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

    public ProductoService(ProductoRepository productoRepository,
                           ProductoSucursalRepository productoSucursalRepository,
                           CategoriaRepository categoriaRepository) {
        this.productoRepository = productoRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public List<Producto> getAllProductos() {
        return productoRepository.findAll();
    }

    public Producto getProductoById(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    @Transactional
    public Producto createProducto(String nombre,
                                   String descripcion,
                                   BigDecimal precio,
                                   String imagen,
                                   Long restauranteId,
                                   Long sucursalId) {

        // Crear el producto
        Producto nuevoProducto = new Producto();
        nuevoProducto.setNombre(nombre);
        nuevoProducto.setDescripcion(descripcion);
        nuevoProducto.setPrecio(precio);
        nuevoProducto.setImagen(imagen);
        nuevoProducto.setDisponible(true);
        nuevoProducto.setRestauranteId(restauranteId);

        Producto productoGuardado = productoRepository.save(nuevoProducto);

        // Crear la asociación en producto_sucursal
        ProductoSucursal productoSucursal = new ProductoSucursal();
        productoSucursal.setProductoId(productoGuardado.getId());
        productoSucursal.setSucursalId(sucursalId);
        productoSucursal.setDisponible(true);

        productoSucursalRepository.save(productoSucursal);

        return productoGuardado;
    }

    public Producto updateProducto(Long id,
                                  String nombre,
                                  String descripcion,
                                  BigDecimal precio,
                                  String imagen,
                                  Long categoriaId) {

        return productoRepository.findById(id).map(producto -> {
            producto.setNombre(nombre);
            producto.setDescripcion(descripcion);
            producto.setPrecio(precio);
            producto.setImagen(imagen);
            producto.setCategoriaId(categoriaId);
            // Preservar el valor actual de disponible y restauranteId
            return productoRepository.save(producto);
        }).orElse(null);
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
