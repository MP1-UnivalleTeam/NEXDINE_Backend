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
    private String estado;

    public void realizarPedido() {
        // TODO: Implementar realización de pedido
    }

    public void cancelarPedido() {
        // TODO: Implementar cancelación de pedido
    }

    public void guardarFavorito() {
        // TODO: Implementar guardado en favoritos
    }

    public void aplicarCupon() {
        // TODO: Implementar aplicación de cupón
    }
}
