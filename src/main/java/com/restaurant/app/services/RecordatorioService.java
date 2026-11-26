package com.restaurant.app.services;

import com.restaurant.app.model.Recordatorio;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.RecordatorioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF027: recordatorios automáticos.
 *
 * Las horas se interpretan en la zona horaria del restaurante
 * (APP_ZONA_HORARIA, por defecto America/Bogota). El programador revisa cada
 * minuto; un recordatorio se dispara una vez por ocurrencia y, si el
 * servidor estuvo apagado, solo recupera ocurrencias de las últimas 12 horas.
 */
@Service
public class RecordatorioService {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioService.class);
    private static final Set<String> ROLES = Set.of("ADMINISTRADOR", "SUPERADMIN");
    private static final Set<String> FRECUENCIAS = Set.of("UNA_VEZ", "DIARIA", "SEMANAL", "MENSUAL");
    private static final Set<String> CANALES = Set.of("APP", "CORREO");
    private static final Duration VENTANA = Duration.ofHours(12);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DESTINATARIOS = 10;

    private final RecordatorioRepository repository;
    private final UsuarioContextService usuarioContextService;
    private final CorreoService correoService;
    private final ZoneId zona;

    public RecordatorioService(RecordatorioRepository repository,
                               UsuarioContextService usuarioContextService,
                               CorreoService correoService,
                               @Value("${app.zona-horaria:America/Bogota}") String zona) {
        this.repository = repository;
        this.usuarioContextService = usuarioContextService;
        this.correoService = correoService;
        this.zona = ZoneId.of(zona);
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(zona);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Usuario actor) {
        UsuarioContextService.UsuarioContext contexto = contexto(actor);
        return repository.findByRestauranteIdOrderByCreadoEnAsc(contexto.getRestauranteId()).stream()
                .filter(r -> visible(r, contexto))
                .map(this::aMapa).toList();
    }

    /** Recordatorios del canal APP que se dispararon después de {@code desde}. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> avisos(Usuario actor, LocalDateTime desde) {
        UsuarioContextService.UsuarioContext contexto = contexto(actor);
        LocalDateTime limite = desde != null ? desde : ahora().minusMinutes(5);
        return repository.findByRestauranteIdAndUltimaEjecucionAfter(contexto.getRestauranteId(), limite).stream()
                .filter(r -> visible(r, contexto))
                .filter(r -> canales(r).contains("APP"))
                .map(this::aMapa).toList();
    }

    @Transactional
    public Map<String, Object> crear(Usuario actor, Map<String, Object> datos) {
        UsuarioContextService.UsuarioContext contexto = contexto(actor);
        Recordatorio r = new Recordatorio();
        r.setRestauranteId(contexto.getRestauranteId());
        r.setSucursalId(contexto.getSucursalId());
        r.setCreadoPor(actor.getId());
        aplicar(r, datos);
        return aMapa(repository.save(r));
    }

    @Transactional
    public Map<String, Object> actualizar(Usuario actor, UUID id, Map<String, Object> datos) {
        Recordatorio r = cargar(actor, id);
        aplicar(r, datos);
        // Al editar o reactivar no se disparan de golpe las ocurrencias ya pasadas.
        r.setUltimaEjecucion(ahora());
        return aMapa(repository.save(r));
    }

    @Transactional
    public void eliminar(Usuario actor, UUID id) {
        repository.delete(cargar(actor, id));
    }

    /** Lo llama el programador cada minuto. Devuelve cuántos recordatorios se dispararon. */
    @Transactional
    public int dispararPendientes() {
        LocalDateTime ahora = ahora();
        int disparados = 0;
        for (Recordatorio r : repository.findByActivoTrue()) {
            LocalDateTime ocurrencia = ultimaOcurrencia(r, ahora);
            if (ocurrencia == null || Duration.between(ocurrencia, ahora).compareTo(VENTANA) > 0) {
                continue;
            }
            LocalDateTime referencia = r.getUltimaEjecucion() != null ? r.getUltimaEjecucion()
                    : r.getCreadoEn() != null ? r.getCreadoEn().atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(zona).toLocalDateTime() : LocalDateTime.MIN;
            if (!ocurrencia.isAfter(referencia)) {
                continue;
            }
            r.setUltimaEjecucion(ahora);
            repository.save(r);
            disparados++;
            if (canales(r).contains("CORREO")) {
                for (String destino : destinatarios(r.getDestinatarios())) {
                    correoService.enviarEnSegundoPlano(destino, "Recordatorio: " + r.getTitulo(),
                            (r.getMensaje() != null ? r.getMensaje() : r.getTitulo())
                                    + "\n\nEste es un recordatorio automático de NexDine.");
                }
            }
        }
        if (disparados > 0) {
            logger.info("NEXDINE | {} recordatorio(s) disparado(s).", disparados);
        }
        return disparados;
    }

    // ---------------------------------------------------------------- Programación

    static LocalDateTime ultimaOcurrencia(Recordatorio r, LocalDateTime ahora) {
        LocalTime hora = r.getHora();
        switch (r.getFrecuencia()) {
            case "UNA_VEZ": {
                if (r.getFecha() == null) {
                    return null;
                }
                LocalDateTime momento = r.getFecha().atTime(hora);
                return momento.isAfter(ahora) ? null : momento;
            }
            case "DIARIA": {
                LocalDateTime hoy = ahora.toLocalDate().atTime(hora);
                return hoy.isAfter(ahora) ? hoy.minusDays(1) : hoy;
            }
            case "SEMANAL": {
                Set<Integer> dias = diasSemana(r.getDiasSemana());
                for (int atras = 0; atras <= 7; atras++) {
                    LocalDate dia = ahora.toLocalDate().minusDays(atras);
                    LocalDateTime momento = dia.atTime(hora);
                    if (dias.contains(dia.getDayOfWeek().getValue() % 7) && !momento.isAfter(ahora)) {
                        return momento;
                    }
                }
                return null;
            }
            case "MENSUAL": {
                int diaMes = r.getDiaMes() == null ? 1 : r.getDiaMes();
                LocalDateTime este = diaDelMes(YearMonth.from(ahora), diaMes).atTime(hora);
                return este.isAfter(ahora) ? diaDelMes(YearMonth.from(ahora).minusMonths(1), diaMes).atTime(hora) : este;
            }
            default:
                return null;
        }
    }

    private static LocalDate diaDelMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(Math.max(1, dia), mes.lengthOfMonth()));
    }

    // ---------------------------------------------------------------- Validación

    private void aplicar(Recordatorio r, Map<String, Object> datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Faltan los datos del recordatorio.");
        }
        r.setTipo(texto(datos.get("tipo"), 30, "PERSONALIZADO"));
        r.setTitulo(FacturacionService.obligatorio(datos.get("titulo"), "El título", 120));
        r.setMensaje(FacturacionService.opcional(datos.get("mensaje"), 500));

        String frecuencia = texto(datos.get("frecuencia"), 20, "DIARIA").toUpperCase();
        if (!FRECUENCIAS.contains(frecuencia)) {
            throw new IllegalArgumentException("La frecuencia no es válida.");
        }
        r.setFrecuencia(frecuencia);
        try {
            r.setHora(LocalTime.parse(texto(datos.get("hora"), 8, "")));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La hora debe tener el formato HH:mm.");
        }

        r.setFecha(null);
        r.setDiasSemana(null);
        r.setDiaMes(null);
        if ("UNA_VEZ".equals(frecuencia)) {
            try {
                r.setFecha(LocalDate.parse(texto(datos.get("fecha"), 10, "")));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Indica la fecha del recordatorio.");
            }
        } else if ("SEMANAL".equals(frecuencia)) {
            Set<Integer> dias = new LinkedHashSet<>();
            if (datos.get("diasSemana") instanceof List<?> lista) {
                for (Object dia : lista) {
                    int valor = dia instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(dia));
                    if (valor < 0 || valor > 6) {
                        throw new IllegalArgumentException("Los días de la semana van de 0 (domingo) a 6 (sábado).");
                    }
                    dias.add(valor);
                }
            }
            if (dias.isEmpty()) {
                throw new IllegalArgumentException("Elige al menos un día de la semana.");
            }
            r.setDiasSemana(dias.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        } else if ("MENSUAL".equals(frecuencia)) {
            int dia;
            try {
                dia = Integer.parseInt(String.valueOf(datos.get("diaMes")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Indica el día del mes.");
            }
            if (dia < 1 || dia > 31) {
                throw new IllegalArgumentException("El día del mes va de 1 a 31.");
            }
            r.setDiaMes(dia);
        }

        Set<String> canales = new LinkedHashSet<>();
        if (datos.get("canales") instanceof List<?> lista) {
            for (Object canal : lista) {
                String valor = String.valueOf(canal).trim().toUpperCase();
                if (!CANALES.contains(valor)) {
                    throw new IllegalArgumentException("Canal no válido: " + valor);
                }
                canales.add(valor);
            }
        }
        if (canales.isEmpty()) {
            canales.add("APP");
        }
        r.setCanales(String.join(",", canales));

        String destinatarios = FacturacionService.opcional(datos.get("destinatarios"), 500);
        List<String> correos = destinatarios(destinatarios);
        if (canales.contains("CORREO")) {
            if (correos.isEmpty()) {
                throw new IllegalArgumentException("Indica al menos un correo para el canal Correo.");
            }
            if (correos.size() > MAX_DESTINATARIOS) {
                throw new IllegalArgumentException("Máximo " + MAX_DESTINATARIOS + " correos por recordatorio.");
            }
            for (String correo : correos) {
                if (!CorreoService.esCorreoValido(correo)) {
                    throw new IllegalArgumentException("Correo no válido: " + correo);
                }
            }
        }
        r.setDestinatarios(correos.isEmpty() ? null : String.join(", ", correos));
        r.setActivo(!Boolean.FALSE.equals(datos.get("activo")));
    }

    private Recordatorio cargar(Usuario actor, UUID id) {
        UsuarioContextService.UsuarioContext contexto = contexto(actor);
        Recordatorio r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recordatorio no encontrado."));
        if (!r.getRestauranteId().equals(contexto.getRestauranteId()) || !visible(r, contexto)) {
            throw new IllegalArgumentException("Recordatorio no encontrado.");
        }
        return r;
    }

    private UsuarioContextService.UsuarioContext contexto(Usuario actor) {
        FacturacionService.exigirRol(actor, ROLES);
        return usuarioContextService.obtenerContexto(actor);
    }

    /** El administrador ve los de su sucursal y los generales; el superadmin, todos. */
    private boolean visible(Recordatorio r, UsuarioContextService.UsuarioContext contexto) {
        return contexto.getSucursalId() == null || r.getSucursalId() == null
                || Objects.equals(r.getSucursalId(), contexto.getSucursalId());
    }

    private Map<String, Object> aMapa(Recordatorio r) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", r.getId());
        datos.put("tipo", r.getTipo());
        datos.put("titulo", r.getTitulo());
        datos.put("mensaje", r.getMensaje() != null ? r.getMensaje() : "");
        datos.put("frecuencia", r.getFrecuencia());
        datos.put("hora", HORA.format(r.getHora()));
        datos.put("fecha", r.getFecha() != null ? r.getFecha().toString() : "");
        datos.put("diasSemana", new ArrayList<>(diasSemana(r.getDiasSemana())));
        datos.put("diaMes", r.getDiaMes() != null ? r.getDiaMes() : 1);
        datos.put("canales", new ArrayList<>(canales(r)));
        datos.put("destinatarios", r.getDestinatarios() != null ? r.getDestinatarios() : "");
        datos.put("activo", r.isActivo());
        datos.put("ultimaEjecucion", r.getUltimaEjecucion());
        datos.put("creadoEn", r.getCreadoEn());
        datos.put("sucursalId", r.getSucursalId());
        return datos;
    }

    private static Set<Integer> diasSemana(String valor) {
        if (valor == null || valor.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(valor.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::parseInt).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> canales(Recordatorio r) {
        return Arrays.stream((r.getCanales() == null ? "APP" : r.getCanales()).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> destinatarios(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valor.split("[,;\\s]+")).map(String::trim).filter(s -> !s.isEmpty())
                .distinct().toList();
    }

    private static String texto(Object valor, int max, String porDefecto) {
        String texto = FacturacionService.opcional(valor, max);
        return texto == null ? porDefecto : texto;
    }
}
