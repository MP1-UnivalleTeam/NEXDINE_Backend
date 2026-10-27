package com.restaurant.app.services;

import com.restaurant.app.model.ItemPedido;
import com.restaurant.app.model.Pedido;
import com.restaurant.app.model.Producto;
import com.restaurant.app.repository.ItemPedidoRepository;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.PedidoRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    @Test
    void recalculaSubtotalImpuestoYTotalConTasaConfigurada() {
        PedidoRepository pedidos = mock(PedidoRepository.class);
        ItemPedidoRepository items = mock(ItemPedidoRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setSubtotal(BigDecimal.ZERO);
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setPropina(new BigDecimal("5.00"));
        pedido.setCargos(BigDecimal.ZERO);
        Producto producto = new Producto();
        producto.setId(UUID.randomUUID());
        producto.setNombre("Plato");
        producto.setPrecio(new BigDecimal("100.00"));
        ItemPedido itemGuardado = new ItemPedido();
        itemGuardado.setPedidoId(pedido.getId());
        itemGuardado.setPrecioUnitario(new BigDecimal("100.00"));
        itemGuardado.setAdicionalOpciones(BigDecimal.ZERO);
        itemGuardado.setCantidad(2);
        when(pedidos.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(productos.findById(producto.getId())).thenReturn(Optional.of(producto));
        when(items.save(any(ItemPedido.class))).thenReturn(itemGuardado);
        when(items.findByPedidoId(pedido.getId())).thenReturn(List.of(itemGuardado));
        when(pedidos.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoOpcionService opciones = mock(ProductoOpcionService.class);
        when(opciones.validarSeleccion(any(), any())).thenReturn(
                new ProductoOpcionService.SeleccionOpciones(
                        BigDecimal.ZERO, "[]", List.of(), ""));

        PedidoService service = new PedidoService(
                pedidos,
                items,
                productos,
                mock(ProductoSucursalRepository.class),
                mock(MesaRepository.class),
                mock(SucursalRepository.class),
                mock(UsuarioContextService.class),
                mock(PedidoEventosService.class),
                opciones,
                mock(CuponService.class),
                new BigDecimal("16"));

        service.agregarItem(pedido.getId(), producto.getId(), 2, null, null);

        assertEquals(0, new BigDecimal("200.00").compareTo(pedido.getSubtotal()));
        assertEquals(0, new BigDecimal("32.00").compareTo(pedido.getImpuestos()));
        assertEquals(0, new BigDecimal("237.00").compareTo(pedido.getTotal()));
    }

    @Test
    void calculaImpuestosEnResumenYExponeTodosLosImportes() {
        PedidoRepository pedidos = mock(PedidoRepository.class);
        ItemPedidoRepository items = mock(ItemPedidoRepository.class);
        UUID pedidoId = UUID.randomUUID();
        Pedido pedido = new Pedido();
        pedido.setId(pedidoId);
        pedido.setSubtotal(new BigDecimal("200.00"));
        pedido.setDescuento(new BigDecimal("10.00"));
        pedido.setTasaImpuesto(new BigDecimal("19.00"));
        pedido.setPropina(new BigDecimal("5.00"));
        pedido.setCargos(new BigDecimal("1.00"));
        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setPedidoId(pedidoId);
        item.setProductoNombre("Plato");
        item.setPrecioUnitario(new BigDecimal("100.00"));
        item.setAdicionalOpciones(BigDecimal.ZERO);
        item.setCantidad(2);
        when(pedidos.findById(pedidoId)).thenReturn(Optional.of(pedido));
        when(items.findByPedidoId(pedidoId)).thenReturn(List.of(item));

        ProductoOpcionService opciones = mock(ProductoOpcionService.class);
        when(opciones.leerDetalle(any())).thenReturn(List.of());

        PedidoService service = new PedidoService(
                pedidos,
                items,
                mock(ProductoRepository.class),
                mock(ProductoSucursalRepository.class),
                mock(MesaRepository.class),
                mock(SucursalRepository.class),
                mock(UsuarioContextService.class),
                mock(PedidoEventosService.class),
                opciones,
                mock(CuponService.class),
                new BigDecimal("19"));

        var resumen = service.obtenerResumen(pedidoId);

        assertEquals(0, new BigDecimal("36.10").compareTo((BigDecimal) resumen.get("impuestos")));
        assertEquals(0, new BigDecimal("232.10").compareTo((BigDecimal) resumen.get("total")));
        assertEquals(new BigDecimal("200.00"), resumen.get("subtotal"));
        assertEquals(new BigDecimal("5.00"), resumen.get("propina"));
    }
}
