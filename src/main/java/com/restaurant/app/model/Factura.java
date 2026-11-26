package com.restaurant.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Factura emitida para un pedido (una por pedido). */
@Entity
@Table(name = "facturas")
@Data
@EqualsAndHashCode(callSuper = true)
public class Factura extends EntidadUuid {

    @Column(name = "pedido_id", nullable = false, unique = true)
    private UUID pedidoId;

    @Column(name = "restaurante_id", nullable = false)
    private UUID restauranteId;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(length = 120)
    private String cufe;

    @Column(name = "cliente_nombre", length = 150)
    private String clienteNombre;

    @Column(name = "cliente_tipo_documento", length = 5)
    private String clienteTipoDocumento;

    @Column(name = "cliente_documento", length = 30)
    private String clienteDocumento;

    @Column(name = "cliente_correo", length = 150)
    private String clienteCorreo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "emitida_por")
    private UUID emitidaPor;

    @Column(name = "correo_enviado_a", length = 150)
    private String correoEnviadoA;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;
}
