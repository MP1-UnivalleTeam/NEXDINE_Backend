package com.restaurant.app.services;

import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestión de sucursales del restaurante (solo SUPERADMIN).
 *
 * El restaurante se obtiene SIEMPRE del contexto autenticado:
 * el frontend nunca envía restauranteId.
 */
@Service
public class SucursalService {

    private final SucursalRepository sucursalRepository;
    private final ProductoSucursalRepository productoSucursalRepository;
    private final MesaRepository mesaRepository;
    private final AdministradorSucursalRepository administradorSucursalRepository;
    private final EmpleadoSucursalRepository empleadoSucursalRepository;
    private final UsuarioContextService usuarioContextService;

    public SucursalService(SucursalRepository sucursalRepository,
                          ProductoSucursalRepository productoSucursalRepository,
                          MesaRepository mesaRepository,
                          AdministradorSucursalRepository administradorSucursalRepository,
                          EmpleadoSucursalRepository empleadoSucursalRepository,
                          UsuarioContextService usuarioContextService) {
        this.sucursalRepository = sucursalRepository;
        this.productoSucursalRepository = productoSucursalRepository;
        this.mesaRepository = mesaRepository;
        this.administradorSucursalRepository = administradorSucursalRepository;
        this.empleadoSucursalRepository = empleadoSucursalRepository;
        this.usuarioContextService = usuarioContextService;
    }

    /** Lista todas las sucursales del restaurante del SUPERADMIN. */
    public List<Sucursal> listar(Usuario usuario) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        return sucursalRepository.findByRestauranteId(restauranteId);
    }

    /** Crea una sucursal belonging al restaurante del contexto. */
    @Transactional
    public Sucursal crear(Usuario usuario, String nombre, String direccion, String telefono) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sucursal es obligatorio.");
        }

        String nombreNormalizado = nombre.trim();

        if (sucursalRepository.existsByRestauranteIdAndNombreIgnoreCase(restauranteId, nombreNormalizado)) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre en el restaurante.");
        }

        Sucursal sucursal = new Sucursal();
        sucursal.setRestauranteId(restauranteId);
        sucursal.setNombre(nombreNormalizado);
        sucursal.setDireccion(direccion);
        sucursal.setTelefono(telefono);
        sucursal.setActiva(true);
        sucursal.setFechaCreacion(java.time.LocalDateTime.now());

        // Una sucursal nueva NO recibe productos automáticamente
        return sucursalRepository.save(sucursal);
    }

    /** Actualiza datos básicos. No permite cambiar el restaurante propietario. */
    @Transactional
    public Sucursal actualizar(Usuario usuario, Long sucursalId,
                               String nombre, String direccion, String telefono) {
        Sucursal sucursal = obtenerSucursalDelRestaurante(usuario, sucursalId);

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la sucursal es obligatorio.");
        }

        String nombreNormalizado = nombre.trim();

        if (!sucursal.getNombre().equalsIgnoreCase(nombreNormalizado)
                && sucursalRepository.existsByRestauranteIdAndNombreIgnoreCase(
                        sucursal.getRestauranteId(), nombreNormalizado)) {
            throw new IllegalArgumentException("Ya existe una sucursal con ese nombre en el restaurante.");
        }

        sucursal.setNombre(nombreNormalizado);
        sucursal.setDireccion(direccion);
        sucursal.setTelefono(telefono);

        return sucursalRepository.save(sucursal);
    }

    /** Activa o desactiva la sucursal. No la elimina. */
    @Transactional
    public Sucursal cambiarEstado(Usuario usuario, Long sucursalId, boolean activa) {
        Sucursal sucursal = obtenerSucursalDelRestaurante(usuario, sucursalId);
        sucursal.setActiva(activa);
        return sucursalRepository.save(sucursal);
    }

    /**
     * Elimina la sucursal.
     *
     * Los productos GLOBALES no se tocan: solo desaparecen sus relaciones
     * producto_sucursal. Si hay mesas o personal asignado, se informa al usuario
     * en lugar de producir un error genérico.
     */
    @Transactional
    public void eliminar(Usuario usuario, Long sucursalId) {
        Sucursal sucursal = obtenerSucursalDelRestaurante(usuario, sucursalId);

        // Relations que impiden eliminar de forma segura
        long mesas = mesaRepository.findBySucursalId(sucursalId).size();
        long administradores = administradorSucursalRepository
                .findBySucursalId(sucursalId).size();
        long empleados = empleadoSucursalRepository
                .findBySucursalId(sucursalId).size();

        if (mesas > 0 || administradores > 0 || empleados > 0) {
            StringBuilder detalle = new StringBuilder(
                    "No se puede eliminar la sucursal porque tiene relaciones activas. ");
            if (mesas > 0) {
                detalle.append(mesas).append(" mesa(s). ");
            }
            if (administradores > 0) {
                detalle.append(administradores).append(" administrador(es). ");
            }
            if (empleados > 0) {
                detalle.append(empleados).append(" empleado(s). ");
            }
            detalle.append("Desactive o reasigne esas relaciones primero.");
            throw new IllegalStateException(detalle.toString());
        }

        try {
            // Solo se eliminan las RELACIONES con productos.
            // Los productos globales del restaurante permanecen intactos.
            productoSucursalRepository.deleteBySucursalId(sucursalId);
            productoSucursalRepository.flush();
            sucursalRepository.delete(sucursal);
            sucursalRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "No se pudo eliminar la sucursal: tiene relaciones protegidas por la base de datos.");
        }
    }

    /**
     * Obtiene la sucursal validando que pertenezca al restaurante del contexto.
     * Evita que un SUPERADMIN manipule sucursales de otro restaurante por ID.
     */
    private Sucursal obtenerSucursalDelRestaurante(Usuario usuario, Long sucursalId) {
        Long restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();

        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe."));

        if (!sucursal.getRestauranteId().equals(restauranteId)) {
            throw new IllegalStateException("La sucursal no pertenece a su restaurante.");
        }

        return sucursal;
    }
}