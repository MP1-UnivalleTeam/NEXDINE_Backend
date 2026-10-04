package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sesiones")
@Data
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fecha;
    private String idSesion;
    private String idMesa;
    private String ultimaSesion;

    public void crearSesion() {
        // TODO: Implementar creación de sesión
    }

    public void validarSesion() {
        // TODO: Implementar validación de sesión
    }

    public void cerrarSesion() {
        // TODO: Implementar cierre de sesión
    }

    public void expirarSesion() {
        // TODO: Implementar expiración de sesión
    }
}
