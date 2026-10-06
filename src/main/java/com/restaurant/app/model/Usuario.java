package com.restaurant.app.model;

import jakarta.persistence.*;
import jakarta.persistence.Transient;
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

    /**
     * Sucursal actualmente asignada al usuario.
     * NO se persiste: se resuelve desde administrador_sucursal o empleado_sucursal
     * y se expone en el JSON para que el frontend muestre el valor real (RF018).
     */
    @Transient
    private Long sucursalId;
    
    public void iniciarSesion() {}
    public void cerrarSesion() {}
    public void editarPerfil() {}
    public void recibirNotificacion() {}

    
}