package com.restaurant.app.services;

import com.restaurant.app.model.*;
import com.restaurant.app.repository.AdministradorSucursalRepository;
import com.restaurant.app.repository.EmpleadoSucursalRepository;
import com.restaurant.app.repository.UserRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

private final UserRepository userRepository;
private final AdministradorSucursalRepository administradorSucursalRepository;
private final EmpleadoSucursalRepository empleadoSucursalRepository;

public UserService(UserRepository userRepository,
                    AdministradorSucursalRepository administradorSucursalRepository,
                    EmpleadoSucursalRepository empleadoSucursalRepository) {
    this.userRepository = userRepository;
    this.administradorSucursalRepository = administradorSucursalRepository;
    this.empleadoSucursalRepository = empleadoSucursalRepository;
}

public void initAdmin() {

    if (!userRepository.findByNombre("jesus").isPresent()) {

        Administrador admin = new Administrador();

        admin.setNombre("jesus");
        admin.setContraseña("1234");
        admin.setRol("ADMINISTRADOR");
        admin.setEstado(true);
        userRepository.save(admin);
    }
}

public Optional<Usuario> login(String nombre,
                               String contraseña) {

    Optional<Usuario> usuarioOpt =
            userRepository.findByNombre(nombre);

    if (usuarioOpt.isPresent()) {

        Usuario usuario = usuarioOpt.get();

        if (usuario.getContraseña().equals(contraseña)) {

            if (!usuario.isEstado()) {
                return Optional.of(new SuspendedMarker());
            }

            return Optional.of(usuario);
        }
    }

    return Optional.empty();
}

public List<Usuario> getAllUsers() {
    return userRepository.findAll();
}

public Usuario createUser(String nombre,
                          String contraseña,
                          String rol,
                          String celular,
                          String direccion,
                          Long sucursalId) {

    Usuario nuevoUsuario;

    switch (rol) {

        case "ADMINISTRADOR":
            nuevoUsuario = new Administrador();
            break;

        case "CLIENTE":
            nuevoUsuario = new Cliente();
            break;

        case "COCINERO":
            nuevoUsuario = new Cocinero();
            break;

        case "MESERO":
            nuevoUsuario = new Mesero();
            break;

        default:
            nuevoUsuario = new Empleado();
            break;
    }

    nuevoUsuario.setNombre(nombre);
    nuevoUsuario.setContraseña(contraseña);
    nuevoUsuario.setRol(rol);
    nuevoUsuario.setCelular(celular);
    nuevoUsuario.setDireccion(direccion);
    nuevoUsuario.setEstado(true);

    Usuario usuarioGuardado = userRepository.save(nuevoUsuario);

    // ADMINISTRADOR y empleados operativos quedan asociados a una sucursal
    if (sucursalId != null) {
        if ("ADMINISTRADOR".equals(rol)) {
            administradorSucursalRepository.save(
                    new AdministradorSucursal(usuarioGuardado.getId(), sucursalId));
        } else if (usuarioGuardado instanceof Empleado) {
            empleadoSucursalRepository.save(
                    new EmpleadoSucursal(usuarioGuardado.getId(), sucursalId));
        }
    }

    return usuarioGuardado;
}

public void updateUser(@NonNull Long id,
                       String nombre,
                       String rol,
                       String celular,
                       String direccion,
                       Long sucursalId,
                       boolean quitarSucursal) {

    userRepository.findById(id).ifPresent(user -> {

        user.setNombre(nombre);
        user.setCelular(celular);
        user.setRol(rol);
        user.setDireccion(direccion);

        userRepository.save(user);

        boolean esAdmin = "ADMINISTRADOR".equals(rol);
        boolean esEmpleadoOperativo = user instanceof Empleado;

        // Quitar sucursal explícitamente
        if (quitarSucursal) {
            if (esAdmin) {
                eliminarSucursalAdministrador(user.getId());
            } else if (esEmpleadoOperativo) {
                eliminarSucursalEmpleado(user.getId());
            }
            return;
        }

        // Reasignar / asignar sucursal (reemplaza la anterior, sin duplicar)
        if (sucursalId != null) {
            if (esAdmin) {
                asignarSucursalAdministrador(user.getId(), sucursalId);
            } else if (esEmpleadoOperativo) {
                asignarSucursalEmpleado(user.getId(), sucursalId);
            }
        }
    });
}

/**
 * Asigna o REASIGNA la sucursal de un administrador.
 * La relación se trata como asignación actual:
 *  · sin asignación previa → crea
 *  · con otra sucursal     → reemplaza
 *  · con la misma sucursal → no-op (sin error)
 */
private void asignarSucursalAdministrador(Long administradorId, Long sucursalId) {
    List<AdministradorSucursal> actuales = administradorSucursalRepository
            .findByAdministradorId(administradorId);

    // Ya está en esa sucursal: no hay nada que hacer
    if (actuales.size() == 1
            && actuales.get(0).getSucursalId().equals(sucursalId)) {
        return;
    }

    // Reemplazar: eliminar la(s) anterior(es) y crear la nueva
    if (!actuales.isEmpty()) {
        administradorSucursalRepository.deleteAll(actuales);
        administradorSucursalRepository.flush();
    }

    administradorSucursalRepository.save(new AdministradorSucursal(administradorId, sucursalId));
}

private void eliminarSucursalAdministrador(Long administradorId) {
    List<AdministradorSucursal> actuales = administradorSucursalRepository
            .findByAdministradorId(administradorId);
    if (!actuales.isEmpty()) {
        administradorSucursalRepository.deleteAll(actuales);
    }
}

private void asignarSucursalEmpleado(Long empleadoId, Long sucursalId) {
    List<EmpleadoSucursal> actuales = empleadoSucursalRepository
            .findByEmpleadoId(empleadoId);

    if (actuales.size() == 1
            && actuales.get(0).getSucursalId().equals(sucursalId)) {
        return;
    }

    if (!actuales.isEmpty()) {
        empleadoSucursalRepository.deleteAll(actuales);
        empleadoSucursalRepository.flush();
    }

    empleadoSucursalRepository.save(new EmpleadoSucursal(empleadoId, sucursalId));
}

private void eliminarSucursalEmpleado(Long empleadoId) {
    List<EmpleadoSucursal> actuales = empleadoSucursalRepository
            .findByEmpleadoId(empleadoId);
    if (!actuales.isEmpty()) {
        empleadoSucursalRepository.deleteAll(actuales);
    }
}

public void deleteUser(@NonNull Long id) {

    userRepository.deleteById(id);
}

public void toggleUserStatus(@NonNull Long id) {

    userRepository.findById(id).ifPresent(usuario -> {

        usuario.setEstado(!usuario.isEstado());

        userRepository.save(usuario);
    });
}

public Optional<Usuario> getUserById(@NonNull Long id) {

    return userRepository.findById(id);
}

/**
 * Resuelve la sucursal actualmente asignada a un usuario.
 * ADMINISTRADOR → administrador_sucursal
 * Empleado      → empleado_sucursal
 * CLIENTE       → null
 */
public Long obtenerSucursalAsignada(Usuario usuario) {

    if (usuario == null) {
        return null;
    }

    if ("ADMINISTRADOR".equals(usuario.getRol())) {
        return administradorSucursalRepository.findByAdministradorId(usuario.getId())
                .stream().findFirst()
                .map(AdministradorSucursal::getSucursalId)
                .orElse(null);
    }

    if (usuario instanceof Empleado) {
        return empleadoSucursalRepository.findByEmpleadoId(usuario.getId())
                .stream().findFirst()
                .map(EmpleadoSucursal::getSucursalId)
                .orElse(null);
    }

    return null;
}

/**
 * Anota cada usuario con su sucursal real para que el frontend
 * muestre el valor correcto en el selector de edición (RF018).
 */
public List<Usuario> getUsuariosConSucursal() {
    List<Usuario> usuarios = userRepository.findAll();
    usuarios.forEach(u -> u.setSucursalId(obtenerSucursalAsignada(u)));
    return usuarios;
}

public static class SuspendedMarker extends Cliente {

    public SuspendedMarker() {
        super();
    }
}


}
