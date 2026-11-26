package com.restaurant.app.config;

import com.restaurant.app.services.RecordatorioService;
import com.restaurant.app.services.ReporteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Tareas automáticas:
 * - RF017 / RF042: registra cada 5 minutos el total de ventas y pedidos del
 *   día (y cierra el día anterior). Al arrancar rellena los últimos días.
 * - RF027: dispara los recordatorios cada minuto.
 */
@Component
public class TareasProgramadas {

    private static final Logger logger = LoggerFactory.getLogger(TareasProgramadas.class);

    private final ReporteService reporteService;
    private final RecordatorioService recordatorioService;
    private final boolean habilitadas;
    private final int diasRelleno;

    public TareasProgramadas(ReporteService reporteService,
                             RecordatorioService recordatorioService,
                             @Value("${app.tareas.habilitadas:true}") boolean habilitadas,
                             @Value("${app.reportes.dias-relleno:60}") int diasRelleno) {
        this.reporteService = reporteService;
        this.recordatorioService = recordatorioService;
        this.habilitadas = habilitadas;
        this.diasRelleno = diasRelleno;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rellenarResumenes() {
        if (!habilitadas) {
            return;
        }
        try {
            LocalDate hoy = LocalDate.now();
            for (int atras = diasRelleno; atras >= 0; atras--) {
                reporteService.registrarDia(hoy.minusDays(atras));
            }
            logger.info("NEXDINE | Resumen diario de ventas actualizado ({} días).", diasRelleno + 1);
        } catch (RuntimeException e) {
            logger.warn("NEXDINE | No se pudo rellenar el resumen diario: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void registrarVentasDelDia() {
        if (!habilitadas) {
            return;
        }
        try {
            LocalDate hoy = LocalDate.now();
            reporteService.registrarDia(hoy.minusDays(1));
            reporteService.registrarDia(hoy);
        } catch (RuntimeException e) {
            logger.warn("NEXDINE | No se pudo registrar el resumen diario: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void dispararRecordatorios() {
        if (!habilitadas) {
            return;
        }
        try {
            recordatorioService.dispararPendientes();
        } catch (RuntimeException e) {
            logger.warn("NEXDINE | Error revisando recordatorios: {}", e.getMessage());
        }
    }
}
