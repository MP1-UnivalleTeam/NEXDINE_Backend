package com.restaurant.app.services;

import com.restaurant.app.model.Categoria;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.CategoriaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestión de categorías del restaurante (solo SUPERADMIN).
 * El restaurante se obtiene siempre del contexto autenticado.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioContextService usuarioContextService;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            UsuarioContextService usuarioContextService) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioContextService = usuarioContextService;
    }

    public List<Categoria> listar(Usuario usuario) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        return categoriaRepository.findByRestauranteId(restauranteId);
    }

    /** Categorías activas: alimenta el select del formulario de producto. */
    public List<Categoria> listarActivas(Usuario usuario) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        return categoriaRepository.findByRestauranteIdAndActivaTrue(restauranteId);
    }

    @Transactional
    public Categoria crear(Usuario usuario, String nombre, String descripcion) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
        }

        String nombreNormalizado = nombre.trim();

        if (categoriaRepository.existsByRestauranteIdAndNombreIgnoreCase(restauranteId, nombreNormalizado)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre en el restaurante.");
        }

        Categoria categoria = new Categoria();
        categoria.setRestauranteId(restauranteId);
        categoria.setNombre(nombreNormalizado);
        categoria.setDescripcion(descripcion);
        categoria.setActiva(true);

        return categoriaRepository.save(categoria);
    }

    @Transactional
    public Categoria actualizar(Usuario usuario, Long categoriaId,
                                String nombre, String descripcion, Boolean activa) {
        Categoria categoria = obtenerCategoriaDelRestaurante(usuario, categoriaId);

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
        }

        String nombreNormalizado = nombre.trim();

        if (!categoria.getNombre().equalsIgnoreCase(nombreNormalizado)
                && categoriaRepository.existsByRestauranteIdAndNombreIgnoreCase(
                        categoria.getRestauranteId(), nombreNormalizado)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre en el restaurante.");
        }

        categoria.setNombre(nombreNormalizado);
        categoria.setDescripcion(descripcion);
        if (activa != null) {
            categoria.setActiva(activa);
        }

        return categoriaRepository.save(categoria);
    }

    private Categoria obtenerCategoriaDelRestaurante(Usuario usuario, Long categoriaId) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();

        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe."));

        if (!categoria.getRestauranteId().equals(restauranteId)) {
            throw new IllegalStateException("La categoría no pertenece a su restaurante.");
        }

        return categoria;
    }
}