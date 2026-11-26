package com.restaurant.app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // Nunca se envía en las respuestas JSON (se guarda como hash BCrypt).
    @JsonIgnore
    private String contraseña;

    private boolean estado = true;

    // RF029: personalización del perfil.
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @JsonIgnore
    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    @Column(name = "color_acento", length = 7)
    private String colorAcento;

    @Column(name = "tema", length = 10)
    private String tema;

    @Transient
    private UUID sucursalId;

    public void iniciarSesion() {}
    public void cerrarSesion() {}
    public void editarPerfil() {}
    public void recibirNotificacion() {}


}
