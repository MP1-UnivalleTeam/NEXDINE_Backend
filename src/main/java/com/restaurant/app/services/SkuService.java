package com.restaurant.app.services;

import com.restaurant.app.model.Restaurante;
import com.restaurant.app.repository.RestauranteRepository;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkuService {

    private static final Logger log = LoggerFactory.getLogger(SkuService.class);

    private static final int INTENTOS_MAXIMOS_CODIGO = 5;

    private final RestauranteRepository restauranteRepository;

    // Solo el perfil "local" (base H2 de pruebas) activa este modo: H2 no
    // entiende el INSERT ... ON CONFLICT ... RETURNING de PostgreSQL.
    // En Supabase/Render queda en false y se usa la consulta original.
    private JdbcTemplate jdbcTemplate;
    private boolean consecutivoPortable;

    public SkuService(RestauranteRepository restauranteRepository) {
        this.restauranteRepository = restauranteRepository;
    }

    @Autowired(required = false)
    void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Value("${app.sku.consecutivo-portable:false}")
    void setConsecutivoPortable(boolean consecutivoPortable) {
        this.consecutivoPortable = consecutivoPortable;
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
        if (consecutivoPortable && jdbcTemplate != null) {
            return reservarConsecutivoPortable(restauranteId);
        }
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

    /** Versión SQL estándar del consecutivo, usada solo en la prueba local. */
    private long reservarConsecutivoPortable(UUID restauranteId) {
        List<Long> actual = jdbcTemplate.queryForList(
                "SELECT ultimo_numero FROM producto_sku_consecutivos WHERE restaurante_id = ? FOR UPDATE",
                Long.class, restauranteId);
        if (actual.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO producto_sku_consecutivos (restaurante_id, ultimo_numero, actualizado_en) "
                            + "VALUES (?, 1, CURRENT_TIMESTAMP)", restauranteId);
            return 1;
        }
        long siguiente = actual.get(0) + 1;
        jdbcTemplate.update(
                "UPDATE producto_sku_consecutivos SET ultimo_numero = ?, actualizado_en = CURRENT_TIMESTAMP "
                        + "WHERE restaurante_id = ?", siguiente, restauranteId);
        return siguiente;
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
