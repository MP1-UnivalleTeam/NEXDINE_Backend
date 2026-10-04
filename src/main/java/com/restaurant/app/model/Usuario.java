package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String celular;
    private String correo;
    private String direccion;
    private String rol;

    private String contraseña;

    private boolean estado = true;
    
    public void iniciarSesion() {}
    public void cerrarSesion() {}
    public void editarPerfil() {}
    public void recibirNotificacion() {}

    
}