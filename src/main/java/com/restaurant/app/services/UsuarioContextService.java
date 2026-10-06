package com.restaurant.app.services;

import com.restaurant.app.model.*;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioContextService {

    private final AdministradorSucursalRepository administradorSucursalRepository;
    private final SucursalRepository sucursalRepository;
    private final RestauranteRepository restauranteRepository;

    public UsuarioContextService(AdministradorSucursalRepository administradorSucursalRepository,
                                  SucursalRepository sucursalRepository,
                                  RestauranteRepository restauranteRepository) {
        this.administradorSucursalRepository = administradorSucursalRepository;
        this.sucursalRepository = sucursalRepository;
        this.restauranteRepository = restauranteRepository;
    }

    /**
     * Obtiene el contexto del usuario autenticado.
     * Resuelve: usuario -> administrador -> sucursal -> restaurante
     *
     * @param usuario usuario autenticado
     * @return UsuarioContext con restauranteId y sucursalId
     * @throws IllegalStateException si el usuario no tiene asociaciones válidas
     */
    public UsuarioContext obtenerContexto(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }

        // Buscar asociaciones del administrador
        List<AdministradorSucursal> asociaciones = administradorSucursalRepository
                .findByAdministradorId(usuario.getId());

        if (asociaciones.isEmpty()) {
            throw new IllegalStateException(
                    "El administrador no tiene sucursales asignadas. Contacte al superadmin.");
        }

        // Tomar la primera sucursal asociada
        Long sucursalId = asociaciones.get(0).getSucursalId();

        // Obtener la sucursal
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException(
                        "La sucursal asignada no existe. Contacte al superadmin."));

        // Obtener el restaurante
        Restaurante restaurante = restauranteRepository.findById(sucursal.getRestauranteId())
                .orElseThrow(() -> new IllegalStateException(
                        "El restaurante de la sucursal no existe. Contacte al superadmin."));

        return new UsuarioContext(restaurante.getId(), sucursal.getId());
    }

    /**
     * Verifica si un administrador tiene acceso a un restaurante específico.
     */
    public boolean tieneAccesoARestaurante(Usuario usuario, Long restauranteId) {
        try {
            UsuarioContext context = obtenerContexto(usuario);
            return context.getRestauranteId().equals(restauranteId);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Clase interna que representa el contexto del usuario.
     */
    public static class UsuarioContext {
        private final Long restauranteId;
        private final Long sucursalId;

        public UsuarioContext(Long restauranteId, Long sucursalId) {
            this.restauranteId = restauranteId;
            this.sucursalId = sucursalId;
        }

        public Long getRestauranteId() {
            return restauranteId;
        }

        public Long getSucursalId() {
            return sucursalId;
        }
    }
}
