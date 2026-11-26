package com.restaurant.app.services;

import com.restaurant.app.model.HorarioSucursal;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.HorarioSucursalRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * RF044: horarios de atención por sucursal.
 *
 * Una sucursal sin horarios configurados atiende siempre. Si tiene horarios,
 * solo recibe pedidos de clientes dentro de ellos. Un cierre anterior a la
 * apertura (ej. 18:00 → 02:00) significa que el turno pasa la medianoche.
 */
@Service
public class HorarioService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private final HorarioSucursalRepository horarioRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioContextService usuarioContextService;
    private final ZoneId zona;

    public HorarioService(HorarioSucursalRepository horarioRepository,
                          SucursalRepository sucursalRepository,
                          UsuarioContextService usuarioContextService,
                          @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.horarioRepository = horarioRepository;
        this.sucursalRepository = sucursalRepository;
        this.usuarioContextService = usuarioContextService;
        this.zona = ZoneId.of(zona);
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(zona);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(UUID sucursalId) {
        sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe."));
        List<HorarioSucursal> horarios = horarioRepository.findBySucursalIdOrderByDiaSemanaAsc(sucursalId);
        LocalDateTime ahora = ahora();
        boolean abierto = estaAbierto(horarios, ahora);

        List<Map<String, Object>> dias = new ArrayList<>();
        for (HorarioSucursal h : horarios) {
            Map<String, Object> dia = new LinkedHashMap<>();
            dia.put("diaSemana", h.getDiaSemana());
            dia.put("horaApertura", HORA.format(h.getHoraApertura()));
            dia.put("horaCierre", HORA.format(h.getHoraCierre()));
            dia.put("activo", h.isActivo());
            dias.add(dia);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("sucursalId", sucursalId);
        respuesta.put("configurado", !horarios.isEmpty());
        respuesta.put("abiertoAhora", abierto);
        respuesta.put("horarios", dias);
        respuesta.put("mensaje", abierto ? "" : mensajeCerrado(horarios, ahora));
        return respuesta;
    }

    @Transactional
    public Map<String, Object> guardar(Usuario actor, UUID sucursalId, List<Map<String, Object>> dias) {
        if (actor == null || !Set.of("ADMINISTRADOR", "SUPERADMIN").contains(actor.getRol())) {
            throw new IllegalStateException("Solo un administrador puede configurar horarios.");
        }
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal no existe."));
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(actor);
        if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())
                || (contexto.getSucursalId() != null && !contexto.getSucursalId().equals(sucursalId))) {
            throw new IllegalStateException("No tiene permisos sobre esta sucursal.");
        }
        if (dias == null) {
            throw new IllegalArgumentException("Debe enviar la lista de horarios.");
        }

        List<HorarioSucursal> nuevos = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        for (Map<String, Object> dia : dias) {
            int diaSemana = entero(dia.get("diaSemana"));
            if (diaSemana < 1 || diaSemana > 7 || !vistos.add(diaSemana)) {
                throw new IllegalArgumentException("Cada día de la semana (1 a 7) debe aparecer una sola vez.");
            }
            LocalTime apertura = hora(dia.get("horaApertura"));
            LocalTime cierre = hora(dia.get("horaCierre"));
            if (apertura.equals(cierre)) {
                throw new IllegalArgumentException("La hora de apertura y la de cierre no pueden ser iguales.");
            }
            HorarioSucursal horario = new HorarioSucursal();
            horario.setSucursalId(sucursalId);
            horario.setDiaSemana(diaSemana);
            horario.setHoraApertura(apertura);
            horario.setHoraCierre(cierre);
            horario.setActivo(!Boolean.FALSE.equals(dia.get("activo")));
            nuevos.add(horario);
        }

        horarioRepository.deleteAll(horarioRepository.findBySucursalIdOrderByDiaSemanaAsc(sucursalId));
        horarioRepository.flush();
        horarioRepository.saveAll(nuevos);
        return consultar(sucursalId);
    }

    /** Lanza IllegalArgumentException si la sucursal está cerrada en este momento. */
    @Transactional(readOnly = true)
    public void exigirAbierta(UUID sucursalId) {
        if (sucursalId == null) {
            return;
        }
        List<HorarioSucursal> horarios = horarioRepository.findBySucursalIdOrderByDiaSemanaAsc(sucursalId);
        LocalDateTime ahora = ahora();
        if (!estaAbierto(horarios, ahora)) {
            throw new IllegalArgumentException(mensajeCerrado(horarios, ahora));
        }
    }

    static boolean estaAbierto(List<HorarioSucursal> horarios, LocalDateTime momento) {
        if (horarios == null || horarios.isEmpty()) {
            return true;
        }
        int hoy = momento.getDayOfWeek().getValue();
        int ayer = momento.getDayOfWeek().minus(1).getValue();
        LocalTime hora = momento.toLocalTime();
        for (HorarioSucursal h : horarios) {
            if (!h.isActivo()) {
                continue;
            }
            boolean cruzaMedianoche = h.getHoraCierre().isBefore(h.getHoraApertura());
            if (h.getDiaSemana() == hoy) {
                if (!cruzaMedianoche && !hora.isBefore(h.getHoraApertura()) && hora.isBefore(h.getHoraCierre())) {
                    return true;
                }
                if (cruzaMedianoche && !hora.isBefore(h.getHoraApertura())) {
                    return true;
                }
            }
            if (h.getDiaSemana() == ayer && cruzaMedianoche && hora.isBefore(h.getHoraCierre())) {
                return true;
            }
        }
        return false;
    }

    private String mensajeCerrado(List<HorarioSucursal> horarios, LocalDateTime ahora) {
        Optional<String> proxima = proximaApertura(horarios, ahora);
        return proxima.map(texto -> "La sucursal está cerrada en este momento. Abre " + texto + ".")
                .orElse("La sucursal está cerrada en este momento.");
    }

    private Optional<String> proximaApertura(List<HorarioSucursal> horarios, LocalDateTime ahora) {
        for (int adelante = 0; adelante <= 7; adelante++) {
            LocalDateTime dia = ahora.plusDays(adelante);
            int diaSemana = dia.getDayOfWeek().getValue();
            for (HorarioSucursal h : horarios) {
                if (h.isActivo() && h.getDiaSemana() == diaSemana
                        && (adelante > 0 || h.getHoraApertura().isAfter(ahora.toLocalTime()))) {
                    String cuando = adelante == 0 ? "hoy"
                            : adelante == 1 ? "mañana"
                            : "el " + DayOfWeek.of(diaSemana).getDisplayName(TextStyle.FULL, ES);
                    return Optional.of(cuando + " a las " + HORA.format(h.getHoraApertura()));
                }
            }
        }
        return Optional.empty();
    }

    private int entero(Object valor) {
        if (valor instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(valor));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El día de la semana no es válido.");
        }
    }

    private LocalTime hora(Object valor) {
        try {
            return LocalTime.parse(String.valueOf(valor));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Las horas deben tener el formato HH:mm.");
        }
    }
}
