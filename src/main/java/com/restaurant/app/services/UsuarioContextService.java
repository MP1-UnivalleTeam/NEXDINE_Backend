package com.restaurant.app.services;

import com.restaurant.app.model.Empleado;
import com.restaurant.app.model.EmpleadoSucursal;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.RestauranteSuperadmin;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.RestauranteSuperadminRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioContextService {

    private final AdministradorSucursalRepository administradorSucursalRepository;
    private final SucursalRepository sucursalRepository;
    private final RestauranteRepository restauranteRepository;
    private final RestauranteSuperadminRepository restauranteSuperadminRepository;
    private final EmpleadoSucursalRepository empleadoSucursalRepository;

    public UsuarioContextService(AdministradorSucursalRepository administradorSucursalRepository,
                                  SucursalRepository sucursalRepository,
                                  RestauranteRepository restauranteRepository,
                                  RestauranteSuperadminRepository restauranteSuperadminRepository,
                                  EmpleadoSucursalRepository empleadoSucursalRepository) {
        this.administradorSucursalRepository = administradorSucursalRepository;
        this.sucursalRepository = sucursalRepository;
        this.restauranteRepository = restauranteRepository;
        this.restauranteSuperadminRepository = restauranteSuperadminRepository;
        this.empleadoSucursalRepository = empleadoSucursalRepository;
    }

    /**
     * Resuelve el contexto del usuario autenticado según su rol.
     *
     * SUPERADMIN  → usuarios.id → restaurante_superadmins → restaurante
     * ADMINISTRADOR → administrador_sucursal → sucursal → restaurante
     *
     * @param usuario usuario autenticado
     * @return UsuarioContext con restauranteId y (opcional) sucursalId
     * @throws IllegalStateException si no se puede resolver el contexto
     */
    public UsuarioContext obtenerContexto(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }

        if ("SUPERADMIN".equals(usuario.getRol())) {
            return obtenerContextoSuperadmin(usuario);
        }

        return obtenerContextoAdministrador(usuario);
    }

    /**
     * SUPERADMIN: resuelve restaurante desde restaurante_superadmins.
     * No depende de sucursal.
     */
    private UsuarioContext obtenerContextoSuperadmin(Usuario usuario) {
        Optional<RestauranteSuperadmin> vinculo = restauranteSuperadminRepository
                .findFirstByUsuarioId(usuario.getId());

        if (vinculo.isEmpty()) {
            throw new IllegalStateException(
                    "El superadmin no tiene un restaurante asociado. Contacte al administrador del sistema.");
        }

        Restaurante restaurante = restauranteRepository.findById(vinculo.get().getRestauranteId())
                .orElseThrow(() -> new IllegalStateException(
                        "El restaurante asociado al superadmin no existe."));

        // sucursalId puede ser null: SUPERADMIN opera con alcance global
        return new UsuarioContext(restaurante.getId(), null, usuario.getRol());
    }

    /**
     * ADMINISTRADOR: resuelve sucursal desde administrador_sucursal,
     * luego restaurante desde esa sucursal.
     */
    private UsuarioContext obtenerContextoAdministrador(Usuario usuario) {
        List<com.restaurant.app.model.AdministradorSucursal> asociaciones = administradorSucursalRepository
                .findByAdministradorId(usuario.getId());

        if (asociaciones.isEmpty()) {
            throw new IllegalStateException(
                    "El administrador no tiene sucursales asignadas. Contacte al superadmin.");
        }

        Long sucursalId = asociaciones.get(0).getSucursalId();

        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException(
                        "La sucursal asignada no existe. Contacte al superadmin."));

        Restaurante restaurante = restauranteRepository.findById(sucursal.getRestauranteId())
                .orElseThrow(() -> new IllegalStateException(
                        "El restaurante de la sucursal no existe. Contacte al superadmin."));

        return new UsuarioContext(restaurante.getId(), sucursal.getId(), usuario.getRol());
    }

    /**
     * Obtiene la sucursal del usuario si tiene una asociada (ADMINISTRADOR).
     * Para SUPERADMIN devuelve Optional.empty().
     */
    public Optional<Long> obtenerSucursalId(Usuario usuario) {
        if (usuario == null || "SUPERADMIN".equals(usuario.getRol())) {
            return Optional.empty();
        }

        return administradorSucursalRepository.findByAdministradorId(usuario.getId())
                .stream()
                .findFirst()
                .map(com.restaurant.app.model.AdministradorSucursal::getSucursalId);
    }

    /**
     * Verifica si el usuario tiene acceso a un restaurante específico.
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
     * Indica si el usuario es un administrador operativo de sucursal
     * (ADMINISTRADOR, MESERO, COCINERO, EMPLEADO con sucursal asignada).
     */
    public boolean esOperativoDeSucursal(Usuario usuario) {
        return usuario != null && !"SUPERADMIN".equals(usuario.getRol()) && !"CLIENTE".equals(usuario.getRol());
    }

    /**
     * IDs de empleados operativos asignados a una sucursal.
     */
    public List<Long> listarIdsEmpleadosDeSucursal(Long sucursalId) {
        if (sucursalId == null) {
            return List.of();
        }
        return empleadoSucursalRepository.findBySucursalId(sucursalId).stream()
                .map(EmpleadoSucursal::getEmpleadoId)
                .toList();
    }

    /**
     * Indica si un empleado pertenece a la sucursal indicada.
     */
    public boolean empleadoPerteneceASucursal(Long empleadoId, Long sucursalId) {
        if (empleadoId == null || sucursalId == null) {
            return false;
        }
        return empleadoSucursalRepository
                .findByEmpleadoIdAndSucursalId(empleadoId, sucursalId)
                .isPresent();
    }

    /**
     * Contexto resuelto para el usuario.
     * sucursalId es null para SUPERADMIN (alcance global).
     */
    public static class UsuarioContext {
        private final Long restauranteId;
        private final Long sucursalId;
        private final String rol;

        public UsuarioContext(Long restauranteId, Long sucursalId, String rol) {
            this.restauranteId = restauranteId;
            this.sucursalId = sucursalId;
            this.rol = rol;
        }

        public Long getRestauranteId() {
            return restauranteId;
        }

        /** Puede ser null para SUPERADMIN. */
        public Long getSucursalId() {
            return sucursalId;
        }

        public String getRol() {
            return rol;
        }

        public boolean esSuperadmin() {
            return "SUPERADMIN".equals(rol);
        }
    }
}