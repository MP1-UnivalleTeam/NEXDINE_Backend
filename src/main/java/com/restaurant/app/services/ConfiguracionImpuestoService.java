package com.restaurant.app.services;

import com.restaurant.app.dto.ConfiguracionImpuestoRequest;
import com.restaurant.app.dto.ConfiguracionImpuestoResponse;
import com.restaurant.app.model.ConfiguracionImpuesto;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.ConfiguracionImpuestoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ConfiguracionImpuestoService {

    private final ConfiguracionImpuestoRepository configuracionRepository;
    private final UsuarioContextService usuarioContextService;
    private final CalculadoraPedidoService calculadora;
    private final BigDecimal porcentajeInicial;

    public ConfiguracionImpuestoService(
            ConfiguracionImpuestoRepository configuracionRepository,
            UsuarioContextService usuarioContextService,
            CalculadoraPedidoService calculadora,
            @Value("${app.pedidos.tasa-impuesto:0}") BigDecimal porcentajeInicial) {
        this.configuracionRepository = configuracionRepository;
        this.usuarioContextService = usuarioContextService;
        this.calculadora = calculadora;
        this.porcentajeInicial = calculadora.validarPorcentaje(porcentajeInicial);
    }

    @Transactional(readOnly = true)
    public ConfiguracionImpuestoResponse consultar(Usuario usuario) {
        exigirPersonal(usuario);
        UUID restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        return configuracionRepository.findByRestauranteId(restauranteId)
                .map(ConfiguracionImpuestoResponse::desde)
                .orElseGet(() -> ConfiguracionImpuestoResponse.porDefecto(
                        restauranteId, porcentajeInicial));
    }

    @Transactional
    public ConfiguracionImpuestoResponse actualizar(Usuario usuario,
                                                    ConfiguracionImpuestoRequest solicitud) {
        exigirSuperadmin(usuario);
        if (solicitud == null || solicitud.porcentaje() == null) {
            throw new IllegalArgumentException("Debe indicar el porcentaje de impuesto.");
        }
        BigDecimal porcentaje = calculadora.validarPorcentaje(solicitud.porcentaje());
        UUID restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        ConfiguracionImpuesto configuracion = prepararConfiguracion(restauranteId);
        configuracion.setPorcentaje(porcentaje);
        if (solicitud.activo() != null) {
            configuracion.setActivo(solicitud.activo());
        }
        configuracion.setModificadoPorUsuarioId(usuario.getId());
        configuracion.setModificadoPorNombre(usuario.getNombre());
        return ConfiguracionImpuestoResponse.desde(configuracionRepository.save(configuracion));
    }

    @Transactional
    public ConfiguracionImpuestoResponse cambiarEstado(Usuario usuario, Boolean activo) {
        exigirSuperadmin(usuario);
        if (activo == null) {
            throw new IllegalArgumentException("Debe indicar si el impuesto está activo.");
        }
        UUID restauranteId = usuarioContextService.obtenerContexto(usuario).getRestauranteId();
        ConfiguracionImpuesto configuracion = prepararConfiguracion(restauranteId);
        configuracion.setActivo(activo);
        configuracion.setModificadoPorUsuarioId(usuario.getId());
        configuracion.setModificadoPorNombre(usuario.getNombre());
        return ConfiguracionImpuestoResponse.desde(configuracionRepository.save(configuracion));
    }

    @Transactional(readOnly = true)
    public BigDecimal obtenerTasaVigente(UUID restauranteId) {
        if (restauranteId == null) {
            return BigDecimal.ZERO;
        }
        return configuracionRepository.findByRestauranteId(restauranteId)
                .map(configuracion -> configuracion.isActivo()
                        ? configuracion.getPorcentaje()
                        : BigDecimal.ZERO)
                .orElse(porcentajeInicial);
    }

    private ConfiguracionImpuesto prepararConfiguracion(UUID restauranteId) {
        if (restauranteId == null) {
            throw new IllegalArgumentException("El restaurante es obligatorio.");
        }
        return configuracionRepository.findByRestauranteId(restauranteId)
                .orElseGet(() -> {
                    ConfiguracionImpuesto configuracion = new ConfiguracionImpuesto();
                    configuracion.setRestauranteId(restauranteId);
                    configuracion.setPorcentaje(porcentajeInicial);
                    configuracion.setActivo(true);
                    return configuracion;
                });
    }

    private void exigirPersonal(Usuario usuario) {
        if (!usuarioContextService.esPersonal(usuario)) {
            throw new IllegalStateException(
                    "Se requiere una sesión de personal autorizado para consultar los impuestos.");
        }
    }

    private void exigirSuperadmin(Usuario usuario) {
        if (usuario == null || !"SUPERADMIN".equals(usuario.getRol())) {
            throw new IllegalStateException(
                    "Solo el SUPERADMIN puede modificar la configuración de impuestos.");
        }
    }
}
