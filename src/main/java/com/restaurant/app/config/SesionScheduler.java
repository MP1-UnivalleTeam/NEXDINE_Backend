package com.restaurant.app.config;

import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.repository.SesionMesaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SesionScheduler {

    @Autowired
    private SesionMesaRepository sesionMesaRepository;

    private static final int MINUTOS_INACTIVIDAD = 15;

    @Scheduled(fixedRate = 60000)
    public void expirarSesionesInactivas() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);

        List<SesionMesa> sesiones = sesionMesaRepository.findAll();
        for (SesionMesa sesion : sesiones) {
            if (sesion.getUltimaActividad().isBefore(limite) && sesion.isActiva()) {
                sesion.setActiva(false);
                sesionMesaRepository.save(sesion);
            }
        }
    }
}
