package com.restaurant.app.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Envío de correos (facturas, comprobantes de reembolso, avisos de estado y
 * recordatorios).
 *
 * Se configura con las variables SPRING_MAIL_HOST, SPRING_MAIL_PORT,
 * SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD y APP_CORREO_REMITENTE. Si
 * faltan, el backend arranca igual y los envíos responden
 * {@link CorreoNoConfiguradoException}.
 */
@Service
public class CorreoService {

    private static final Logger logger = LoggerFactory.getLogger(CorreoService.class);
    private static final Pattern CORREO_VALIDO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final long MAX_ADJUNTO_BYTES = 5L * 1024 * 1024;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String host;
    private final String remitente;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "nexdine-correo");
        hilo.setDaemon(true);
        return hilo;
    });

    public CorreoService(ObjectProvider<JavaMailSender> mailSenderProvider,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${spring.mail.username:}") String usuario,
                         @Value("${app.correo.remitente:}") String remitente) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host == null ? "" : host.trim();
        this.remitente = remitente != null && !remitente.isBlank() ? remitente.trim()
                : (usuario == null ? "" : usuario.trim());
    }

    public boolean estaConfigurado() {
        return !host.isEmpty() && !remitente.isEmpty() && mailSenderProvider.getIfAvailable() != null;
    }

    public static boolean esCorreoValido(String correo) {
        return correo != null && correo.length() <= 150 && CORREO_VALIDO.matcher(correo.trim()).matches();
    }

    public static String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            return null;
        }
        String limpio = correo.trim();
        if (!esCorreoValido(limpio)) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }
        return limpio;
    }

    /** Envía el correo en este mismo hilo; lanza excepción si no se pudo. */
    public void enviar(String para, String asunto, String texto, Adjunto adjunto) {
        if (!esCorreoValido(para)) {
            throw new IllegalArgumentException("El correo de destino no es válido.");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (host.isEmpty() || remitente.isEmpty() || sender == null) {
            throw new CorreoNoConfiguradoException();
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mensaje, adjunto != null, StandardCharsets.UTF_8.name());
            helper.setFrom(remitente);
            helper.setTo(para.trim());
            helper.setSubject(asunto);
            helper.setText(texto, false);
            if (adjunto != null) {
                helper.addAttachment(adjunto.nombre(),
                        new ByteArrayResource(adjunto.contenido()), adjunto.tipo());
            }
            sender.send(mensaje);
        } catch (MessagingException | MailException e) {
            logger.warn("NEXDINE | No se pudo enviar el correo \"{}\": {}", asunto, e.getMessage());
            throw new CorreoFallidoException();
        }
    }

    /** Envía en segundo plano; los fallos solo quedan en el log. */
    public void enviarEnSegundoPlano(String para, String asunto, String texto) {
        if (!estaConfigurado() || !esCorreoValido(para)) {
            return;
        }
        ejecutor.submit(() -> {
            try {
                enviar(para, asunto, texto, null);
            } catch (RuntimeException e) {
                logger.warn("NEXDINE | Aviso por correo no enviado: {}", e.getMessage());
            }
        });
    }

    /** Valida que el archivo recibido sea un PDF razonable y lo convierte en adjunto. */
    public static Adjunto adjuntoPdf(MultipartFile archivo, String nombre) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar el PDF.");
        }
        if (archivo.getSize() > MAX_ADJUNTO_BYTES) {
            throw new IllegalArgumentException("El PDF supera el tamaño máximo de 5 MB.");
        }
        try {
            byte[] contenido = archivo.getBytes();
            if (contenido.length < 5 || contenido[0] != '%' || contenido[1] != 'P'
                    || contenido[2] != 'D' || contenido[3] != 'F') {
                throw new IllegalArgumentException("El archivo adjunto no es un PDF válido.");
            }
            String nombreSeguro = nombre.replaceAll("[^A-Za-z0-9._-]", "_");
            return new Adjunto(nombreSeguro, contenido, "application/pdf");
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el PDF adjunto.");
        }
    }

    public record Adjunto(String nombre, byte[] contenido, String tipo) {
    }

    public static class CorreoNoConfiguradoException extends RuntimeException {
        public CorreoNoConfiguradoException() {
            super("El envío de correos no está configurado en el servidor.");
        }
    }

    public static class CorreoFallidoException extends RuntimeException {
        public CorreoFallidoException() {
            super("El servidor de correo no aceptó el mensaje. Inténtalo nuevamente.");
        }
    }
}
