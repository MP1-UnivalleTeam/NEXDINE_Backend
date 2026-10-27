package com.restaurant.app.services;

import com.restaurant.app.model.AdministradorSucursal;
import com.restaurant.app.model.Cliente;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.RestauranteSuperadmin;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.RestauranteSuperadminRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.UUID;
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

    public UsuarioContext obtenerContexto(Usuario usuario) {
        if (usuario == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }

        if ("SUPERADMIN".equals(usuario.getRol())) {
            return obtenerContextoSuperadmin(usuario);
        }

        if ("MESERO".equals(usuario.getRol()) || "COCINERO".equals(usuario.getRol())) {
            return obtenerContextoEmpleado(usuario);
        }

        return obtenerContextoAdministrador(usuario);
    }

    private UsuarioContext obtenerContextoEmpleado(Usuario usuario) {
        UUID sucursalId = empleadoSucursalRepository.findByEmpleadoId(usuario.getId())
                .stream()
                .findFirst()
                .map(asociacion -> asociacion.getSucursalId())
                .orElseThrow(() -> new IllegalStateException(
                        "El empleado no tiene una sucursal asignada."));

        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException(
                        "La sucursal asignada al empleado no existe."));

        return new UsuarioContext(sucursal.getRestauranteId(), sucursalId, usuario.getRol());
    }

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

        return new UsuarioContext(restaurante.getId(), null, usuario.getRol());
    }

    private UsuarioContext obtenerContextoAdministrador(Usuario usuario) {
        List<AdministradorSucursal> asociaciones = administradorSucursalRepository
                .findByAdministradorId(usuario.getId());

        if (asociaciones.isEmpty()) {
            throw new IllegalStateException(
                    "El administrador no tiene sucursales asignadas. Contacte al superadmin.");
        }

        UUID sucursalId = asociaciones.get(0).getSucursalId();

        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalStateException(
                        "La sucursal asignada no existe. Contacte al superadmin."));

        Restaurante restaurante = restauranteRepository.findById(sucursal.getRestauranteId())
                .orElseThrow(() -> new IllegalStateException(
                        "El restaurante de la sucursal no existe. Contacte al superadmin."));

        return new UsuarioContext(restaurante.getId(), sucursal.getId(), usuario.getRol());
    }

    public Optional<UUID> obtenerSucursalId(Usuario usuario) {
        if (usuario == null || "SUPERADMIN".equals(usuario.getRol())) {
            return Optional.empty();
        }

        return administradorSucursalRepository.findByAdministradorId(usuario.getId())
                .stream()
                .findFirst()
                .map(asociacion -> asociacion.getSucursalId());
    }

    public boolean tieneAccesoARestaurante(Usuario usuario, UUID restauranteId) {
        try {
            UsuarioContext context = obtenerContexto(usuario);
            return context.getRestauranteId().equals(restauranteId);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public UUID exigirSucursalDelContexto(Usuario actor, UUID sucursalId) {
        if (sucursalId == null) {
            throw new IllegalArgumentException("Debe indicar una sucursal.");
        }

        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal indicada no existe."));

        UUID restauranteContexto = obtenerContexto(actor).getRestauranteId();

        if (!sucursal.getRestauranteId().equals(restauranteContexto)) {
            throw new IllegalStateException("La sucursal indicada no pertenece a su restaurante.");
        }

        return sucursalId;
    }

    public void exigirRecursoEnContexto(Usuario actor, UUID restauranteIdDelRecurso, String descripcion) {
        if (restauranteIdDelRecurso == null) {
            return;
        }

        UUID restauranteContexto = obtenerContexto(actor).getRestauranteId();

        if (!restauranteIdDelRecurso.equals(restauranteContexto)) {
            throw new IllegalStateException(
                    "No tiene permisos sobre " + descripcion + ": pertenece a otro restaurante.");
        }
    }

    public void exigirUsuarioEnContexto(Usuario actor, Usuario objetivo) {
        if (objetivo == null || objetivo instanceof Cliente) {
            return;
        }

        Optional<UUID> restauranteObjetivo = restauranteDeUsuario(objetivo);

        UUID idObjetivo = restauranteObjetivo.orElseGet(() -> {
            try {
                return obtenerContexto(objetivo).getRestauranteId();
            } catch (IllegalStateException e) {
                return null;
            }
        });

        if (idObjetivo == null) {
            return;
        }

        exigirRecursoEnContexto(actor, idObjetivo, "este usuario");
    }

    public Optional<UUID> restauranteDeUsuario(Usuario usuario) {
        if (usuario == null) {
            return Optional.empty();
        }

        if ("SUPERADMIN".equals(usuario.getRol())) {
            return restauranteSuperadminRepository.findFirstByUsuarioId(usuario.getId())
                    .map(vinculo -> vinculo.getRestauranteId());
        }

        Optional<UUID> sucursalId = administradorSucursalRepository
                .findByAdministradorId(usuario.getId())
                .stream()
                .findFirst()
                .map(asociacion -> asociacion.getSucursalId());

        if (sucursalId.isEmpty()) {
            sucursalId = empleadoSucursalRepository.findByEmpleadoId(usuario.getId())
                    .stream()
                    .findFirst()
                    .map(asociacion -> asociacion.getSucursalId());
        }

        UUID sucursalResuelta = sucursalId.orElse(null);

        if (sucursalResuelta == null) {
            return Optional.empty();
        }

        return sucursalRepository.findById(sucursalResuelta)
                .map(sucursal -> sucursal.getRestauranteId());
    }

    public boolean esOperativoDeSucursal(Usuario usuario) {
        return usuario != null && !"SUPERADMIN".equals(usuario.getRol()) && !"CLIENTE".equals(usuario.getRol());
    }

    public List<UUID> listarIdsEmpleadosDeSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return List.of();
        }
        return empleadoSucursalRepository.findBySucursalId(sucursalId).stream()
                .map(asociacion -> asociacion.getEmpleadoId())
                .toList();
    }

    public boolean empleadoPerteneceASucursal(UUID empleadoId, UUID sucursalId) {
        if (empleadoId == null || sucursalId == null) {
            return false;
        }
        return empleadoSucursalRepository
                .findByEmpleadoIdAndSucursalId(empleadoId, sucursalId)
                .isPresent();
    }

    public static class UsuarioContext {
        private final UUID restauranteId;
        private final UUID sucursalId;
        private final String rol;

        public UsuarioContext(UUID restauranteId, UUID sucursalId, String rol) {
            this.restauranteId = restauranteId;
            this.sucursalId = sucursalId;
            this.rol = rol;
        }

        public UUID getRestauranteId() {
            return restauranteId;
        }

        @Nullable
        public UUID getSucursalId() {
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
