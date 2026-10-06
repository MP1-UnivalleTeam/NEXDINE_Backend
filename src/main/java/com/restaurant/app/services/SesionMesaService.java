package com.restaurant.app.services;

import com.restaurant.app.model.SesionMesa;
import com.restaurant.app.repository.SesionMesaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SesionMesaService {

    @Autowired
    private SesionMesaRepository sesionMesaRepository;

    private static final int MINUTOS_INACTIVIDAD = 15;

    /**
     * Crea una sesión para la mesa o reutiliza una activa y vigente.
     * Una sesión expirada NUNCA se reutiliza: se marca inactiva y se genera
     * una nueva con token nuevo.
     */
    public SesionMesa crearSesion(String idMesa) {
        Optional<SesionMesa> existente = sesionMesaRepository
            .findByIdMesaAndActiva(idMesa, true);

        if (existente.isPresent()) {
            SesionMesa sesion = existente.get();

            LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);

            // Sesión vencida por inactividad: invalidar y crear una nueva
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

        Optional<SesionMesa> sesion = sesionMesaRepository.findByToken(token);

        if (sesion.isEmpty() || !sesion.get().isActiva()) {
            return false;
        }

        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD);
        if (sesion.get().getUltimaActividad() == null
                || sesion.get().getUltimaActividad().isBefore(limite)) {
            sesion.get().setActiva(false);
            sesionMesaRepository.save(sesion.get());
            return false;
        }

        sesion.get().setUltimaActividad(LocalDateTime.now());
        sesionMesaRepository.save(sesion.get());

        return true;
    }

    /**
 * Comprueba si una sesión existe y sigue vigente SIN renovar su actividad.
 *
 * Se usa durante el polling del menú: consultar el catálogo no debe
 * contar como actividad del cliente ni extender la sesión silenciosamente.
 */
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

    /**
     * Devuelve el código de mesa asociado a una sesión vigente.
     */
    public Optional<String> obtenerMesaDeSesion(String token) {

        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        return sesionMesaRepository.findByToken(token)
                .filter(s -> s.isActiva()
                        && s.getUltimaActividad() != null
                        && !s.getUltimaActividad().isBefore(
                                LocalDateTime.now().minusMinutes(MINUTOS_INACTIVIDAD)))
                .map(SesionMesa::getIdMesa);
    }

    /**
 * Tipos de servicio permitidos por RF003.
 */
public static final String TIPO_DOMICILIO = "DOMICILIO";
    public static final String TIPO_PARA_LLEVAR = "PARA_LLEVAR";

    /**
     * RF003 — Registra el tipo de servicio elegido por el cliente.
     *
     * Reglas:
     *  · Solo DOMICILIO o PARA_LLEVAR. Cualquier otro valor se rechaza.
     *  · No crea sesión nueva ni renueva la vigencia de la existente.
     *  · Si la sesión expiró, se rechaza con estado inválido.
     */
    @Transactional
    public String registrarTipoServicio(String token, String tipoServicio) {

        String tipo = tipoServicio == null ? null : tipoServicio.trim().toUpperCase();

        if (!TIPO_DOMICILIO.equals(tipo) && !TIPO_PARA_LLEVAR.equals(tipo)) {
            throw new IllegalArgumentException("Tipo de servicio inválido.");
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Sesión no válida.");
        }

        Optional<SesionMesa> sesion = sesionMesaRepository.findByToken(token);

        if (sesion.isEmpty() || !sesion.get().isActiva()) {
            throw new IllegalStateException("Tu sesión ha expirado. Escanea nuevamente el código QR.");
        }

        sesion.get().setTipoServicio(tipo);
        sesionMesaRepository.save(sesion.get());

        return tipo;
    }

    /**
     * Devuelve el tipo de servicio registrado en una sesión, o null.
     */
    public Optional<String> obtenerTipoServicio(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sesionMesaRepository.findByToken(token)
                .map(SesionMesa::getTipoServicio);
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
