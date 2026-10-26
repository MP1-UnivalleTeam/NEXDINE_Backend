package com.restaurant.app.services;

import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProductoRepository productoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         ItemPedidoRepository itemPedidoRepository,
                         ProductoRepository productoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public Pedido crearPedidoInvitado(String nombre, String telefono, String tipoServicio,
                                      String direccion, String referencia) {
        Pedido pedido = new Pedido();
        pedido.setNombre(nombre);
        pedido.setTelefono(telefono);
        pedido.setTipoServicio(tipoServicio);
        pedido.setDireccion(direccion);
        pedido.setReferencia(referencia);
        pedido.setEstado("PENDIENTE");
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public ItemPedido agregarItem(UUID pedidoId, UUID productoId, int cantidad,
                                  List<UUID> opcionIds, String observaciones) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        ItemPedido item = new ItemPedido();
        item.setPedidoId(pedidoId);
        item.setProductoId(productoId);
        item.setProductoNombre(producto.getNombre());
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrecio());
        item.setAdicionalOpciones(BigDecimal.ZERO);
        item.setOpcionIds(opcionIds != null ? String.join(",", opcionIds.stream().map(String::valueOf).toList()) : null);
        item.setOpcionNombres("");
        item.setObservaciones(observaciones);

        ItemPedido guardado = itemPedidoRepository.save(item);

        recalcularTotales(pedido);
        return guardado;
    }

    @Transactional
    public Pedido confirmarPedido(UUID pedidoId, String propinaTipo, BigDecimal propinaValor,
                                  String cuponCodigo) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        if (propinaValor != null && propinaValor.compareTo(BigDecimal.ZERO) > 0) {
            pedido.setPropinaTipo(propinaTipo);
            pedido.setPropinaValor(propinaValor);
            pedido.setPropina(propinaValor);
        }

        if (cuponCodigo != null && !cuponCodigo.isBlank()) {
            pedido.setCuponCodigo(cuponCodigo);
            BigDecimal descuento = calcularDescuento(cuponCodigo, pedido.getSubtotal());
            pedido.setDescuento(descuento);
        }

        recalcularTotales(pedido);
        pedido.setEstado("CONFIRMADO");
        return pedidoRepository.save(pedido);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        List<ItemPedido> items = itemPedidoRepository.findByPedidoId(pedidoId);

        List<Map<String, Object>> itemsResponse = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemPedido item : items) {
            BigDecimal importe = item.getPrecioUnitario()
                    .add(item.getAdicionalOpciones())
                    .multiply(BigDecimal.valueOf(item.getCantidad()));
            subtotal = subtotal.add(importe);

            StringBuilder personalizaciones = new StringBuilder();
            if (item.getOpcionNombres() != null && !item.getOpcionNombres().isBlank()) {
                personalizaciones.append(item.getOpcionNombres());
            }

            itemsResponse.add(Map.of(
                    "id", item.getId(),
                    "nombre", item.getProductoNombre(),
                    "cantidad", item.getCantidad(),
                    "precioUnitario", item.getPrecioUnitario(),
                    "importe", importe,
                    "personalizaciones", personalizaciones.toString(),
                    "observaciones", item.getObservaciones() != null ? item.getObservaciones() : ""
            ));
        }

        BigDecimal total = subtotal
                .subtract(pedido.getDescuento())
                .add(pedido.getPropina())
                .add(pedido.getCargos());

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("id", pedido.getId());
        resumen.put("numeroSeguimiento", pedido.getNumeroSeguimiento());
        resumen.put("estado", pedido.getEstado());
        resumen.put("tipoServicio", pedido.getTipoServicio());
        resumen.put("nombre", pedido.getNombre());
        resumen.put("telefono", pedido.getTelefono());
        resumen.put("direccion", pedido.getDireccion() != null ? pedido.getDireccion() : "");
        resumen.put("referencia", pedido.getReferencia() != null ? pedido.getReferencia() : "");
        resumen.put("items", itemsResponse);
        resumen.put("subtotal", subtotal);
        resumen.put("descuento", pedido.getDescuento());
        resumen.put("cargos", pedido.getCargos());
        resumen.put("propina", pedido.getPropina());
        resumen.put("total", total);
        resumen.put("cuponCodigo", pedido.getCuponCodigo() != null ? pedido.getCuponCodigo() : "");
        resumen.put("pagoMetodo", pedido.getPagoMetodo() != null ? pedido.getPagoMetodo() : "");
        resumen.put("pagoEstado", pedido.getPagoEstado() != null ? pedido.getPagoEstado() : "");
        return resumen;
    }

    private void recalcularTotales(Pedido pedido) {
        List<ItemPedido> items = itemPedidoRepository.findByPedidoId(pedido.getId());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : items) {
            BigDecimal importe = item.getPrecioUnitario()
                    .add(item.getAdicionalOpciones())
                    .multiply(BigDecimal.valueOf(item.getCantidad()));
            subtotal = subtotal.add(importe);
        }

        pedido.setSubtotal(subtotal);

        BigDecimal total = subtotal
                .subtract(pedido.getDescuento())
                .add(pedido.getPropina())
                .add(pedido.getCargos());
        pedido.setTotal(total);
    }

    private BigDecimal calcularDescuento(String cuponCodigo, BigDecimal subtotal) {
        return BigDecimal.ZERO;
    }
}
