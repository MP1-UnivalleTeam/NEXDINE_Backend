package com.restaurant.app.dto;

public record RegistroClienteRequest(
        String nombre,
        String contrasena,
        String celular,
        String direccion,
        String correo) {
}
