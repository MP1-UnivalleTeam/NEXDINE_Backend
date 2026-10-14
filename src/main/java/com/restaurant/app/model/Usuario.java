package com.restaurant.app.model;

import jakarta.persistence.*;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class Usuario extends EntidadUuid {

    private String nombre;
    private String celular;
    private String correo;
    private String direccion;
    private String rol;

    private String contraseña;

    private boolean estado = true;

    @Transient
    private UUID sucursalId;

    public void iniciarSesion() {}
    public void cerrarSesion() {}
    public void editarPerfil() {}
    public void recibirNotificacion() {}


}
