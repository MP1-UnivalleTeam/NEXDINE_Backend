package com.restaurant.app.services;

import com.restaurant.app.model.Cupon;
import com.restaurant.app.repository.CuponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CuponService {

    private static final String PORCENTAJE = "PORCENTAJE";
    private static final String MONTO_FIJO = "MONTO_FIJO";

    private final CuponRepository cuponRepository;

    public CuponService(CuponRepository cuponRepository) {
        this.cuponRepository = cuponRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validar(String codigo, BigDecimal subtotal) {
        Cupon cupon = obtenerValido(codigo, subtotal);
        BigDecimal descuento = calcularDescuento(cupon, subtotal);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("codigo", cupon.getCodigo());
        response.put("tipo", cupon.getTipo());
        response.put("valor", cupon.getValor());
        response.put("montoMinimo", cupon.getMontoMinimo());
        response.put("descuento", descuento);
        return response;
    }

    @Transactional(readOnly = true)
    public CuponAplicado aplicar(String codigo, BigDecimal subtotal) {
        Cupon cupon = obtenerValido(codigo, subtotal);
        return new CuponAplicado(cupon.getId(), cupon.getCodigo(),
                calcularDescuento(cupon, subtotal));
    }

    @Transactional
    public void registrarUso(String codigo) {
        Cupon cupon = cuponRepository.findWithLockByCodigoIgnoreCase(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Cupón inválido o expirado."));
        if (cupon.getUsoMaximo() != null && cupon.getUsos() >= cupon.getUsoMaximo()) {
            throw new IllegalArgumentException("El cupón ya alcanzó su límite de usos.");
        }
        cupon.setUsos(cupon.getUsos() + 1);
        cuponRepository.save(cupon);
    }

    private Cupon obtenerValido(String codigo, BigDecimal subtotal) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código del cupón es obligatorio.");
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        Cupon cupon = cuponRepository.findByCodigoIgnoreCase(normalizado)
                .orElseThrow(() -> new IllegalArgumentException("Cupón inválido o expirado."));

        if (!cupon.isActivo()) {
            throw new IllegalArgumentException("Cupón inválido o expirado.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (cupon.getFechaInicio() != null && ahora.isBefore(cupon.getFechaInicio())) {
            throw new IllegalArgumentException("El cupón todavía no está vigente.");
        }
        if (cupon.getFechaFin() != null && ahora.isAfter(cupon.getFechaFin())) {
            throw new IllegalArgumentException("Cupón inválido o expirado.");
        }
        if (cupon.getUsoMaximo() != null && cupon.getUsos() >= cupon.getUsoMaximo()) {
            throw new IllegalArgumentException("El cupón ya alcanzó su límite de usos.");
        }

        String tipo = cupon.getTipo() == null ? "" : cupon.getTipo().trim().toUpperCase(Locale.ROOT);
        if (!PORCENTAJE.equals(tipo) && !MONTO_FIJO.equals(tipo)) {
            throw new IllegalArgumentException("Cupón inválido o expirado.");
        }
        if (cupon.getValor() == null || cupon.getValor().signum() <= 0) {
            throw new IllegalArgumentException("Cupón inválido o expirado.");
        }
        if (PORCENTAJE.equals(tipo) && cupon.getValor().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Cupón inválido o expirado.");
        }
        if (subtotal != null && cupon.getMontoMinimo() != null
                && subtotal.compareTo(cupon.getMontoMinimo()) < 0) {
            throw new IllegalArgumentException("El subtotal mínimo para este cupón es "
                    + cupon.getMontoMinimo().toPlainString() + ".");
        }
        return cupon;
    }

    private BigDecimal calcularDescuento(Cupon cupon, BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal descuento;
        if (PORCENTAJE.equalsIgnoreCase(cupon.getTipo())) {
            descuento = subtotal.multiply(cupon.getValor())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            descuento = cupon.getValor();
        }
        if (descuento.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return descuento.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public record CuponAplicado(UUID id, String codigo, BigDecimal descuento) {
    }
}
