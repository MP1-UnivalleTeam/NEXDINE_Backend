package com.restaurant.app.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RegistroDuenoRequest(
        String nombre,
        @JsonAlias({"contraseña", "password"}) String contrasena,
        String celular,
        String direccion,
        String correo) {
}
