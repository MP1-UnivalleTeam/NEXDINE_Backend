package com.restaurant.app.services;

import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.repository.SesionMesaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SesionMesaService {

    @Autowired
    private SesionMesaRepository sesionMesaRepository;

    private static final int MINUTOS_INACTIVIDAD = 15;

    public SesionMesa crearSesion(String idMesa) {
        Optional<SesionMesa> existente = sesionMesaRepository
            .findByIdMesaAndActiva(idMesa, true);

        if (existente.isPresent()) {
            SesionMesa sesion = existente.get();
            sesion.setUltimaActividad(LocalDateTime.now());
            return sesionMesaRepository.save(sesion);
        }

        SesionMesa sesion = new SesionMesa();
        sesion.setIdMesa(idMesa);
        sesion.setToken(UUID.randomUUID().toString());
        sesion.setActiva(true);

        return sesionMesaRepository.save(sesion);
    }

    public boolean validarSesion(String token) {
        Optional<SesionMesa> sesion = sesionMesaRepository.findByToken(token);

        if (sesion.isEmpty() || !sesion.get().isActiva()) {
            return false;
        }

        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);
        if (sesion.get().getUltimaActividad().isBefore(limite)) {
            sesion.get().setActiva(false);
            sesionMesaRepository.save(sesion.get());
            return false;
        }

        sesion.get().setUltimaActividad(LocalDateTime.now());
        sesionMesaRepository.save(sesion.get());

        return true;
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
