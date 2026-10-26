package com.restaurant.app.services;

import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoFavorito;
import com.restaurant.app.repository.ProductoFavoritoRepository;
import com.restaurant.app.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductoFavoritoService {

    private final ProductoFavoritoRepository favoritoRepository;
    private final ProductoRepository productoRepository;

    public ProductoFavoritoService(ProductoFavoritoRepository favoritoRepository,
                                   ProductoRepository productoRepository) {
        this.favoritoRepository = favoritoRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public List<Producto> listar(UUID clienteId) {
        return favoritoRepository.findByClienteId(clienteId).stream()
                .map(favorito -> productoRepository.findById(favorito.getProductoId())
                        .orElseThrow(() -> new IllegalStateException(
                                "El producto guardado como favorito ya no existe.")))
                .toList();
    }

    @Transactional
    public Producto agregar(UUID clienteId, UUID productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));

        if (!producto.isDisponible()) {
            throw new IllegalArgumentException("El producto no está disponible.");
        }

        if (favoritoRepository.findByClienteIdAndProductoId(clienteId, productoId).isEmpty()) {
            ProductoFavorito favorito = new ProductoFavorito();
            favorito.setClienteId(clienteId);
            favorito.setProductoId(productoId);
            favoritoRepository.save(favorito);
        }
        return producto;
    }

    @Transactional
    public void eliminar(UUID clienteId, UUID productoId) {
        favoritoRepository.findByClienteIdAndProductoId(clienteId, productoId)
                .ifPresent(favoritoRepository::delete);
    }
}
