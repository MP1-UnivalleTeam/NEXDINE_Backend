package com.restaurant.app.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones_mesa")
@Data
public class SesionMesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idMesa;
    private String token;
    private LocalDateTime fechaCreacion;
    private LocalDateTime ultimaActividad;
    private boolean activa = true;

    /**
     * RF003 — Tipo de servicio elegido por el cliente.
     * Valores permitidos: DOMICILIO, PARA_LLEVAR.
     * NULL mientras el cliente no confirme su selección.
     *
     * Es contexto del cliente para el futuro pedido.
     * No crea una sesión nueva ni renueva la vigencia de la actual.
     */
    @Column(name = "tipo_servicio", length = 20)
    private String tipoServicio;

    public SesionMesa() {
        this.fechaCreacion = LocalDateTime.now();
        this.ultimaActividad = LocalDateTime.now();
    }
}
