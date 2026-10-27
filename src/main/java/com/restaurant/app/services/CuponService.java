package com.restaurant.app.services;

import com.restaurant.app.dto.CuponRequest;
import com.restaurant.app.model.Cupon;
import com.restaurant.app.repository.CuponRepository;
import com.restaurant.app.repository.PedidoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CuponService {

    private static final String PORCENTAJE = "PORCENTAJE";
    private static final String MONTO_FIJO = "MONTO_FIJO";

    private final CuponRepository cuponRepository;
    private final PedidoRepository pedidoRepository;

    public CuponService(CuponRepository cuponRepository, PedidoRepository pedidoRepository) {
        this.cuponRepository = cuponRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public List<Cupon> listarAdministracion() {
        return cuponRepository.findAllByOrderByFechaCreacionDesc();
    }

    @Transactional
    public Cupon crear(CuponRequest solicitud) {
        validarSolicitud(solicitud);
        String codigo = normalizarCodigo(solicitud.codigo());
        if (cuponRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new IllegalArgumentException("Ya existe un cupón con ese código.");
        }

        Cupon cupon = new Cupon();
        aplicarSolicitud(cupon, solicitud, codigo);
        try {
            return cuponRepository.saveAndFlush(cupon);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Ya existe un cupón con ese código.");
        }
    }

    @Transactional
    public Cupon actualizar(UUID id, CuponRequest solicitud) {
        Cupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado."));
        validarSolicitud(solicitud);
        String codigo = normalizarCodigo(solicitud.codigo());
        if (cuponRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new IllegalArgumentException("Ya existe un cupón con ese código.");
        }

        aplicarSolicitud(cupon, solicitud, codigo);
        try {
            return cuponRepository.saveAndFlush(cupon);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Ya existe un cupón con ese código.");
        }
    }

    @Transactional
    public Cupon cambiarEstado(UUID id, boolean activo) {
        Cupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado."));
        cupon.setActivo(activo);
        return cuponRepository.save(cupon);
    }

    @Transactional
    public void eliminar(UUID id) {
        Cupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado."));
        if (cupon.getUsos() > 0 || pedidoRepository.existsByCuponCodigoIgnoreCase(cupon.getCodigo())) {
            throw new IllegalStateException(
                    "El cupón ya tiene historial de pedidos y no se puede eliminar. Desactívalo para impedir nuevos usos.");
        }
        cuponRepository.delete(cupon);
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

    private void validarSolicitud(CuponRequest solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("Debe indicar los datos del cupón.");
        }
        String codigo = normalizarCodigo(solicitud.codigo());
        if (codigo.isBlank() || codigo.length() > 50) {
            throw new IllegalArgumentException("El código es obligatorio y admite hasta 50 caracteres.");
        }

        String tipo = solicitud.tipo() == null
                ? "" : solicitud.tipo().trim().toUpperCase(Locale.ROOT);
        if (!PORCENTAJE.equals(tipo) && !MONTO_FIJO.equals(tipo)) {
            throw new IllegalArgumentException("El tipo debe ser PORCENTAJE o MONTO_FIJO.");
        }
        if (solicitud.valor() == null || solicitud.valor().scale() > 2
                || solicitud.valor().signum() <= 0
                || solicitud.valor().precision() > 10
                || (PORCENTAJE.equals(tipo)
                && solicitud.valor().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(PORCENTAJE.equals(tipo)
                    ? "El porcentaje debe ser mayor que 0 y no superar 100."
                    : "El descuento fijo debe ser mayor que 0 y tener hasta dos decimales.");
        }
        BigDecimal minimo = solicitud.montoMinimo() == null
                ? BigDecimal.ZERO : solicitud.montoMinimo();
        if (minimo.scale() > 2 || minimo.signum() < 0 || minimo.precision() > 10) {
            throw new IllegalArgumentException("El monto mínimo debe ser igual o mayor que 0 y tener hasta dos decimales.");
        }
        if (solicitud.usoMaximo() != null && solicitud.usoMaximo() < 1) {
            throw new IllegalArgumentException("El límite de usos debe ser mayor que 0.");
        }
        if (solicitud.fechaInicio() != null && solicitud.fechaFin() != null
                && !solicitud.fechaFin().isAfter(solicitud.fechaInicio())) {
            throw new IllegalArgumentException("La fecha de expiración debe ser posterior al inicio.");
        }
    }

    private String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    private void aplicarSolicitud(Cupon cupon, CuponRequest solicitud, String codigo) {
        cupon.setCodigo(codigo);
        cupon.setTipo(solicitud.tipo().trim().toUpperCase(Locale.ROOT));
        cupon.setValor(solicitud.valor());
        cupon.setMontoMinimo(solicitud.montoMinimo() == null
                ? BigDecimal.ZERO : solicitud.montoMinimo());
        cupon.setFechaInicio(solicitud.fechaInicio());
        cupon.setFechaFin(solicitud.fechaFin());
        cupon.setUsoMaximo(solicitud.usoMaximo());
        if (solicitud.activo() != null) {
            cupon.setActivo(solicitud.activo());
        }
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
