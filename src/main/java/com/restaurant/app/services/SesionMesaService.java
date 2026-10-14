package com.restaurant.app.services;

import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.repository.SesionMesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SesionMesaService {

    private final SesionMesaRepository sesionMesaRepository;

    public SesionMesaService(SesionMesaRepository sesionMesaRepository) {
        this.sesionMesaRepository = sesionMesaRepository;
    }

    private static final int MINUTOS_INACTIVIDAD = 15;

    public SesionMesa crearSesion(String idMesa) {
        Optional<SesionMesa> existente = sesionMesaRepository
            .findByIdMesaAndActiva(idMesa, true);

        if (existente.isPresent()) {
            SesionMesa sesion = existente.get();

            LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);

            if (sesion.getUltimaActividad() == null
                    || sesion.getUltimaActividad().isBefore(limite)) {
                sesion.setActiva(false);
                sesionMesaRepository.save(sesion);
            } else {
                sesion.setUltimaActividad(LocalDateTime.now());
                return sesionMesaRepository.save(sesion);
            }
        }

        SesionMesa sesion = new SesionMesa();
        sesion.setIdMesa(idMesa);
        sesion.setToken(UUID.randomUUID().toString());
        sesion.setActiva(true);

        return sesionMesaRepository.save(sesion);
    }

    public boolean validarSesion(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        Optional<SesionMesa> optionalSesion = sesionMesaRepository.findByToken(token);

        if (optionalSesion.isEmpty() || !optionalSesion.get().isActiva()) {
            return false;
        }

        SesionMesa sesion = optionalSesion.get();

        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);
        if (sesion.getUltimaActividad() == null
                || sesion.getUltimaActividad().isBefore(limite)) {
            sesion.setActiva(false);
            sesionMesaRepository.save(sesion);
            return false;
        }

        sesion.setUltimaActividad(LocalDateTime.now());
        sesionMesaRepository.save(sesion);

        return true;
    }

    public boolean esSesionVigente(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        Optional<SesionMesa> sesion = sesionMesaRepository.findByToken(token);

        if (sesion.isEmpty() || !sesion.get().isActiva()) {
            return false;
        }

        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);

        return sesion.get().getUltimaActividad() != null
                && !sesion.get().getUltimaActividad().isBefore(limite);
    }

    public Optional<String> obtenerMesaDeSesion(String token) {

        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return sesionMesaRepository.findByToken(token)
                .filter(s -> s.isActiva()
                        && s.getUltimaActividad() != null
                        && !s.getUltimaActividad().isBefore(
                                LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD)))
                .map(s -> s.getIdMesa());
    }

public static final String TIPO_DOMICILIO = "DOMICILIO";
    public static final String TIPO_PARA_LLEVAR = "PARA_LLEVAR";

    @Transactional
    public String registrarTipoServicio(String token, String tipoServicio) {

        String tipo = tipoServicio == null ? null : tipoServicio.trim().toUpperCase();

        if (!TIPO_DOMICILIO.equals(tipo) && !TIPO_PARA_LLEVAR.equals(tipo)) {
            throw new IllegalArgumentException("Tipo de servicio inválido.");
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Sesión no válida.");
        }

        Optional<SesionMesa> optionalSesion = sesionMesaRepository.findByToken(token);

        if (optionalSesion.isEmpty() || !optionalSesion.get().isActiva()) {
            throw new IllegalStateException("Tu sesión ha expirado. Escanea nuevamente el código QR.");
        }

        SesionMesa sesion = optionalSesion.get();

        sesion.setTipoServicio(tipo);
        sesionMesaRepository.save(sesion);

        return tipo;
    }

    public Optional<String> obtenerTipoServicio(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sesionMesaRepository.findByToken(token)
                .map(s -> s.getTipoServicio());
    }

    public void actualizarActividad(String token) {
        sesionMesaRepository.findByToken(token).ifPresent(sesion -> {
            sesion.setUltimaActividad(LocalDateTime.now());
            sesionMesaRepository.save(sesion);
        });
    }

    public void cerrarSesion(String token) {
        sesionMesaRepository.findByToken(token).ifPresent(sesion -> {
            sesion.setActiva(false);
            sesionMesaRepository.save(sesion);
        });
    }
}
