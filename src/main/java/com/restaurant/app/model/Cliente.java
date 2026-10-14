package com.restaurant.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "clientes")
@Data
@EqualsAndHashCode(callSuper = true)
public class Cliente extends Usuario {

    private String idCliente;

    public void realizarPedido() {
    }

    public void cancelarPedido() {
    }

    public void guardarFavorito() {
    }

    public void aplicarCupon() {
    }
}
