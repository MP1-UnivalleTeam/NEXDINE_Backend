package com.restaurant.app.services;

import com.restaurant.app.model.Restaurante;
import com.restaurant.app.repository.RestauranteRepository;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkuService {

    private static final Logger log = LoggerFactory.getLogger(SkuService.class);

    private static final int INTENTOS_MAXIMOS_CODIGO = 5;

    private final RestauranteRepository restauranteRepository;

    public SkuService(RestauranteRepository restauranteRepository) {
        this.restauranteRepository = restauranteRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String generarSku(UUID restauranteId) {
        Restaurante restaurante = obtenerRestaurante(restauranteId);

        String iniciales = asegurarIniciales(restaurante);
        String codigo = asegurarCodigo(restaurante);

        long consecutivo = reservarConsecutivo(restauranteId);

        String sku = SkuUtil.construirSku(iniciales, codigo, consecutivo);

        log.debug("NEXDINE | SKU generado: {} (restaurante={})", sku, restauranteId);
        return sku;
    }

    private long reservarConsecutivo(UUID restauranteId) {
        try {
            Long consecutivo = restauranteRepository.siguienteConsecutivoProducto(restauranteId);

            if (consecutivo == null || consecutivo <= 0) {
                throw new IllegalStateException(
                        "No fue posible asignar el consecutivo del producto. "
                                + "Verifique que la tabla producto_sku_consecutivos exista en la base de datos.");
            }

            return consecutivo;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "No fue posible asignar el consecutivo del producto por una restricción "
                            + "de integridad. Verifique la tabla producto_sku_consecutivos.", e);
        }
    }

    private Restaurante obtenerRestaurante(UUID restauranteId) {
        if (restauranteId == null) {
            throw new IllegalArgumentException("No se pudo determinar el restaurante del producto.");
        }

        return restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new IllegalStateException(
                        "El restaurante asociado al contexto no existe."));
    }

    private String asegurarIniciales(Restaurante restaurante) {
        String actuales = restaurante.getIniciales();

        if (actuales != null && !actuales.isBlank()) {
            return actuales.trim();
        }

        String calculadas = SkuUtil.generarIniciales(restaurante.getNombre());
        restaurante.setIniciales(calculadas);
        restauranteRepository.save(restaurante);

        log.info("NEXDINE | Iniciales asignadas al restaurante {}: {}",
                restaurante.getId(), calculadas);

        return calculadas;
    }

    private String asegurarCodigo(Restaurante restaurante) {
        String actual = restaurante.getCodigo();

        if (actual != null && !actual.isBlank()) {
            return actual.trim();
        }

        for (int intento = 0; intento < INTENTOS_MAXIMOS_CODIGO; intento++) {
            Long numero = restauranteRepository.siguienteNumeroCodigoRestaurante();

            if (numero == null || numero <= 0) {
                throw new IllegalStateException(
                        "No fue posible asignar el código del restaurante. "
                                + "Verifique que la secuencia restaurante_codigo_seq exista.");
            }

            String generado = SkuUtil.formatearCodigo(numero);

            if (!restauranteRepository.existsByCodigo(generado)) {
                restaurante.setCodigo(generado);
                restauranteRepository.save(restaurante);

                log.info("NEXDINE | Código asignado al restaurante {}: {}",
                        restaurante.getId(), generado);

                return generado;
            }
        }

        throw new IllegalStateException(
                "No fue posible asignar un código único al restaurante tras varios intentos. "
                        + "Revise la secuencia restaurante_codigo_seq.");
    }

    @Transactional(readOnly = true)
    public EstadoSku consultarEstado() {
        return new EstadoSku(
                restauranteRepository.countByCodigoIsNull(),
                restauranteRepository.countByInicialesIsNull());
    }

    public record EstadoSku(long restaurantesSinCodigo, long restaurantesSinIniciales) {
        public boolean completo() {
            return restaurantesSinCodigo == 0 && restaurantesSinIniciales == 0;
        }
    }
}
