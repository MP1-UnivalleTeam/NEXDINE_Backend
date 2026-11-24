package com.restaurant.app.services;

import com.restaurant.app.model.ItemPedido;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CalculadoraPedidoService {

    public static final int ESCALA_MONETARIA = 2;
    public static final int ESCALA_PORCENTAJE = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public Desglose calcular(List<ItemPedido> items, BigDecimal descuento,
                             BigDecimal tasaImpuesto, String propinaTipo,
                             BigDecimal propinaValor, BigDecimal propinaGuardada,
                             BigDecimal cargos) {
        BigDecimal subtotal = calcularSubtotal(items);
        return calcular(subtotal, descuento, tasaImpuesto, propinaTipo,
                propinaValor, propinaGuardada, cargos);
    }

    public Desglose calcular(BigDecimal subtotal, BigDecimal descuento,
                             BigDecimal tasaImpuesto, String propinaTipo,
                             BigDecimal propinaValor, BigDecimal propinaGuardada,
                             BigDecimal cargos) {
        BigDecimal subtotalNormalizado = normalizarMoneda(subtotal).max(BigDecimal.ZERO);
        BigDecimal descuentoNormalizado = normalizarMoneda(descuento)
                .max(BigDecimal.ZERO)
                .min(subtotalNormalizado);
        BigDecimal baseGravable = subtotalNormalizado.subtract(descuentoNormalizado)
                .max(BigDecimal.ZERO)
                .setScale(ESCALA_MONETARIA, REDONDEO);
        BigDecimal tasa = normalizarTasa(tasaImpuesto);
        BigDecimal impuestos = calcularPorcentaje(baseGravable, tasa);
        BigDecimal propina = calcularPropina(propinaTipo, propinaValor,
                propinaGuardada, subtotalNormalizado);
        BigDecimal cargosNormalizados = normalizarMoneda(cargos).max(BigDecimal.ZERO);
        BigDecimal total = baseGravable.add(impuestos)
                .add(propina)
                .add(cargosNormalizados)
                .setScale(ESCALA_MONETARIA, REDONDEO);

        return new Desglose(subtotalNormalizado, descuentoNormalizado, baseGravable,
                tasa, impuestos, propina, cargosNormalizados, total);
    }

    public BigDecimal calcularSubtotal(List<ItemPedido> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, REDONDEO);
        }
        return items.stream()
                .map(item -> normalizarMoneda(item.getPrecioUnitario())
                        .add(normalizarMoneda(item.getAdicionalOpciones()))
                        .multiply(BigDecimal.valueOf(item.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, REDONDEO);
    }

    public BigDecimal calcularPorcentaje(BigDecimal base, BigDecimal porcentaje) {
        return normalizarMoneda(base)
                .multiply(normalizarTasa(porcentaje))
                .divide(BigDecimal.valueOf(100), ESCALA_MONETARIA, REDONDEO);
    }

    public BigDecimal normalizarMoneda(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor)
                .setScale(ESCALA_MONETARIA, REDONDEO);
    }

    public BigDecimal validarPorcentaje(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El porcentaje de impuesto es obligatorio.");
        }
        if (valor.stripTrailingZeros().scale() > ESCALA_PORCENTAJE) {
            throw new IllegalArgumentException(
                    "El porcentaje de impuesto admite como máximo dos decimales.");
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0
                || valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "El porcentaje de impuesto debe estar entre 0 y 100.");
        }
        return valor.setScale(ESCALA_PORCENTAJE, REDONDEO);
    }

    public BigDecimal normalizarTasa(BigDecimal tasa) {
        if (tasa == null) {
            return BigDecimal.ZERO.setScale(ESCALA_PORCENTAJE, REDONDEO);
        }
        if (tasa.compareTo(BigDecimal.ZERO) < 0
                || tasa.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "La tasa de impuesto aplicada no es válida.");
        }
        return tasa.setScale(ESCALA_PORCENTAJE, REDONDEO);
    }

    private BigDecimal calcularPropina(String propinaTipo, BigDecimal propinaValor,
                                       BigDecimal propinaGuardada, BigDecimal subtotal) {
        if ("PORCENTAJE".equalsIgnoreCase(propinaTipo) && propinaValor != null) {
            return calcularPorcentaje(subtotal, propinaValor);
        }
        return normalizarMoneda(propinaGuardada).max(BigDecimal.ZERO);
    }

    public record Desglose(
            BigDecimal subtotal,
            BigDecimal descuento,
            BigDecimal baseGravable,
            BigDecimal tasaImpuesto,
            BigDecimal impuestos,
            BigDecimal propina,
            BigDecimal cargos,
            BigDecimal total) {
    }
}
