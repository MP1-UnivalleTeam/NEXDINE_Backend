package com.restaurant.app.services;

import com.restaurant.app.model.*;
import com.restaurant.app.repository.UserRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

private final UserRepository userRepository;

public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
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
                          String direccion) {

    Usuario nuevoUsuario;

    switch (rol) {

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

    return userRepository.save(nuevoUsuario);
}

public void updateUser(@NonNull Long id,
                       String nombre,
                       String rol,
                       String celular,
                       String direccion) {

    userRepository.findById(id).ifPresent(user -> {

        user.setNombre(nombre);
        user.setCelular(celular);
        user.setRol(rol);
        user.setDireccion(direccion);

        userRepository.save(user);
    });
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

public static class SuspendedMarker extends Cliente {

    public SuspendedMarker() {
        super();
    }
}


}
