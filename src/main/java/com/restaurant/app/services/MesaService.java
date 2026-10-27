package com.restaurant.app.services;

import com.restaurant.app.model.Mesa;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.repository.MesaRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private static final String PREFIJO_CODIGO = "mesa-";
    private static final int INTENTOS_MAXIMOS = 10;
    private static final int LONGITUD_SUFIJO = 8;

    private final MesaRepository mesaRepository;
    private final SucursalRepository sucursalRepository;
    private final RestauranteRepository restauranteRepository;
    private final UsuarioContextService usuarioContextService;

    public MesaService(MesaRepository mesaRepository,
                       SucursalRepository sucursalRepository,
                       RestauranteRepository restauranteRepository,
                       UsuarioContextService usuarioContextService) {
        this.mesaRepository = mesaRepository;
        this.sucursalRepository = sucursalRepository;
        this.restauranteRepository = restauranteRepository;
        this.usuarioContextService = usuarioContextService;
    }

    public List<Mesa> listarMesas(Usuario usuario, UUID sucursalIdSolicitada) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);

        UUID sucursalObjetivo = contexto.getSucursalId();

        if (sucursalIdSolicitada != null) {
            if (!contexto.esSuperadmin() && !sucursalIdSolicitada.equals(contexto.getSucursalId())) {
                throw new IllegalStateException("No tiene permisos para consultar otra sucursal.");
            }
            UUID sucursalValidada = sucursalIdSolicitada;
            sucursalObjetivo = sucursalDelRestaurante(sucursalValidada,
                    contexto.getRestauranteId());
        }

        if (sucursalObjetivo == null) {
            return sucursalRepository.findByRestauranteId(contexto.getRestauranteId()).stream()
                    .flatMap(s -> mesaRepository.findBySucursalId(s.getId()).stream())
                    .collect(Collectors.toList());
        }

        return mesaRepository.findBySucursalId(sucursalObjetivo);
    }

    private UUID sucursalDelRestaurante(@NonNull UUID sucursalId, UUID restauranteId) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal indicada no existe."));

        if (!sucursal.getRestauranteId().equals(restauranteId)) {
            throw new IllegalStateException("La sucursal indicada no pertenece a su restaurante.");
        }

        return sucursalId;
    }

    @Transactional
    public Mesa crearMesa(Usuario usuario, UUID sucursalId, String nombre) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la mesa es obligatorio.");
        }

        UUID sucursalObjetivo = contexto.getSucursalId();

        if (sucursalId != null) {
            if (!contexto.esSuperadmin()) {
                throw new IllegalStateException("No tiene permisos para crear mesas en otra sucursal.");
            }
            sucursalObjetivo = sucursalId;
        }

        if (sucursalObjetivo == null) {
            throw new IllegalStateException("Debe seleccionar una sucursal para crear la mesa.");
        }

        Sucursal sucursal = sucursalRepository.findById(sucursalObjetivo)
                .orElseThrow(() -> new IllegalArgumentException("La sucursal indicada no existe."));

        if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())) {
            throw new IllegalStateException("La sucursal indicada no pertenece a su restaurante.");
        }

        String nombreNormalizado = nombre.trim();

        if (mesaRepository.existsBySucursalIdAndNombreIgnoreCase(sucursalObjetivo, nombreNormalizado)) {
            throw new IllegalArgumentException("Ya existe una mesa con ese nombre en la sucursal.");
        }

        Mesa mesa = new Mesa();
        mesa.setSucursalId(sucursalObjetivo);
        mesa.setNombre(nombreNormalizado);
        mesa.setCodigo(generarCodigoUnico());
        mesa.setActiva(true);

        return mesaRepository.save(mesa);
    }

    @Transactional
    public Mesa cambiarEstadoMesa(Usuario usuario, @NonNull UUID mesaId, boolean activa) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new IllegalArgumentException("La mesa no existe."));

        if (contexto.getSucursalId() != null
                && !mesa.getSucursalId().equals(contexto.getSucursalId())) {
            throw new IllegalStateException("No tiene permisos sobre esta mesa.");
        }

        if (contexto.getSucursalId() == null) {
            Sucursal sucursal = sucursalRepository.findById(mesa.getSucursalId())
                    .orElseThrow(() -> new IllegalArgumentException("La sucursal de la mesa no existe."));

            if (!sucursal.getRestauranteId().equals(contexto.getRestauranteId())) {
                throw new IllegalStateException("No tiene permisos sobre esta mesa.");
            }
        }

        mesa.setActiva(activa);
        return mesaRepository.save(mesa);
    }

    public Optional<Mesa> buscarPorCodigoActiva(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return mesaRepository.findByCodigo(codigo.trim())
                .filter(mesa -> mesa.isActiva());
    }

    public List<Sucursal> listarSucursalesPermitidas(Usuario usuario) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);

        UUID sucursalId = contexto.getSucursalId();

        if (sucursalId != null) {
            return sucursalRepository.findById(sucursalId)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        return sucursalRepository.findByRestauranteId(contexto.getRestauranteId());
    }

    @Transactional
    public com.restaurant.app.model.Restaurante obtenerRestauranteConSlug(Usuario usuario) {
        UsuarioContextService.UsuarioContext contexto = usuarioContextService.obtenerContexto(usuario);

        com.restaurant.app.model.Restaurante restaurante = restauranteRepository.findById(contexto.getRestauranteId())
                .orElseThrow(() -> new IllegalStateException("El restaurante no existe."));

        if (restaurante.getSlug() == null || restaurante.getSlug().isBlank()) {
            restaurante.setSlug(generarSlugUnico(restaurante.getNombre()));
            restauranteRepository.save(restaurante);
        }

        return restaurante;
    }

    private String generarSlugUnico(String nombre) {
        String base = SlugUtil.slugify(nombre);
        if (base.isEmpty()) {
            base = "restaurante";
        }

        String candidato = base;
        int sufijo = 2;

        while (restauranteRepository.existsBySlug(candidato)) {
            candidato = base + "-" + sufijo;
            sufijo++;
        }

        return candidato;
    }

    private String generarCodigoUnico() {
        for (int intento = 0; intento < INTENTOS_MAXIMOS; intento++) {
            String codigo = PREFIJO_CODIGO + generarSufijo();
            if (!mesaRepository.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No fue posible generar un código único para la mesa.");
    }

    private String generarSufijo() {
        StringBuilder sb = new StringBuilder(LONGITUD_SUFIJO);
        for (int i = 0; i < LONGITUD_SUFIJO; i++) {
            sb.append(ALFABETO[random.nextInt(ALFABETO.length)]);
        }
        return sb.toString();
    }

    private final SecureRandom random = new SecureRandom();
    private static final char[] ALFABETO = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
}
