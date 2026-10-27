package com.restaurant.app.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.app.model.ProductoOpcion;
import com.restaurant.app.model.ProductoOpcionGrupo;
import com.restaurant.app.repository.ProductoOpcionGrupoRepository;
import com.restaurant.app.repository.ProductoOpcionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductoOpcionService {

    private final ProductoOpcionGrupoRepository grupoRepository;
    private final ProductoOpcionRepository opcionRepository;
    private final ObjectMapper objectMapper;

    public ProductoOpcionService(ProductoOpcionGrupoRepository grupoRepository,
                                 ProductoOpcionRepository opcionRepository,
                                 ObjectMapper objectMapper) {
        this.grupoRepository = grupoRepository;
        this.opcionRepository = opcionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerOpciones(UUID productoId) {
        List<ProductoOpcionGrupo> grupos = grupoRepository
                .findByProductoIdOrderByOrdenAscNombreAsc(productoId);
        if (grupos.isEmpty()) {
            return List.of();
        }

        List<UUID> grupoIds = grupos.stream().map(ProductoOpcionGrupo::getId).toList();
        Map<UUID, List<ProductoOpcion>> opcionesPorGrupo = opcionRepository
                .findByGrupoIdInOrderByOrdenAscNombreAsc(grupoIds).stream()
                .collect(Collectors.groupingBy(ProductoOpcion::getGrupoId));

        List<Map<String, Object>> response = new ArrayList<>();
        for (ProductoOpcionGrupo grupo : grupos) {
            Map<String, Object> grupoResponse = new LinkedHashMap<>();
            grupoResponse.put("id", grupo.getId());
            grupoResponse.put("nombre", grupo.getNombre());
            grupoResponse.put("permiteMultiple", grupo.isPermiteMultiple());
            grupoResponse.put("obligatorio", grupo.isObligatorio());
            grupoResponse.put("orden", grupo.getOrden());

            List<Map<String, Object>> opcionesResponse = new ArrayList<>();
            for (ProductoOpcion opcion : opcionesPorGrupo.getOrDefault(grupo.getId(), List.of())) {
                Map<String, Object> opcionResponse = new LinkedHashMap<>();
                opcionResponse.put("id", opcion.getId());
                opcionResponse.put("nombre", opcion.getNombre());
                opcionResponse.put("precio", opcion.getPrecioAdicional());
                opcionResponse.put("precioAdicional", opcion.getPrecioAdicional());
                opcionResponse.put("disponible", opcion.isDisponible());
                opcionResponse.put("orden", opcion.getOrden());
                opcionesResponse.add(opcionResponse);
            }
            grupoResponse.put("opciones", opcionesResponse);
            response.add(grupoResponse);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public boolean tieneOpciones(UUID productoId) {
        return grupoRepository.existsByProductoId(productoId);
    }

    @Transactional(readOnly = true)
    public SeleccionOpciones validarSeleccion(UUID productoId, List<UUID> opcionIds) {
        List<UUID> seleccionadas = opcionIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(opcionIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .toList()));

        List<ProductoOpcionGrupo> grupos = grupoRepository
                .findByProductoIdOrderByOrdenAscNombreAsc(productoId);
        if (grupos.isEmpty()) {
            if (!seleccionadas.isEmpty()) {
                throw new IllegalArgumentException("El producto no admite opciones de personalización.");
            }
            return new SeleccionOpciones(BigDecimal.ZERO, "[]", List.of(), "");
        }

        List<UUID> grupoIds = grupos.stream().map(ProductoOpcionGrupo::getId).toList();
        Map<UUID, ProductoOpcion> opcionesPorId = opcionRepository
                .findByGrupoIdInOrderByOrdenAscNombreAsc(grupoIds).stream()
                .collect(Collectors.toMap(ProductoOpcion::getId, Function.identity()));

        Map<UUID, ProductoOpcionGrupo> gruposPorId = grupos.stream()
                .collect(Collectors.toMap(ProductoOpcionGrupo::getId, Function.identity()));

        Map<UUID, List<ProductoOpcion>> seleccionPorGrupo = new LinkedHashMap<>();
        List<Map<String, Object>> detalle = new ArrayList<>();
        BigDecimal adicional = BigDecimal.ZERO;

        for (UUID opcionId : seleccionadas) {
            ProductoOpcion opcion = opcionesPorId.get(opcionId);
            if (opcion == null) {
                throw new IllegalArgumentException("La opción seleccionada no pertenece al producto.");
            }
            if (!opcion.isDisponible()) {
                throw new IllegalArgumentException("La opción " + opcion.getNombre() + " no está disponible.");
            }
            if (opcion.getPrecioAdicional() != null && opcion.getPrecioAdicional().signum() < 0) {
                throw new IllegalStateException("El precio de la opción no es válido.");
            }

            ProductoOpcionGrupo grupo = gruposPorId.get(opcion.getGrupoId());
            if (grupo == null) {
                throw new IllegalArgumentException("La opción seleccionada no pertenece al producto.");
            }

            seleccionPorGrupo.computeIfAbsent(grupo.getId(), clave -> new ArrayList<>()).add(opcion);
            adicional = adicional.add(opcion.getPrecioAdicional() == null
                    ? BigDecimal.ZERO : opcion.getPrecioAdicional());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", opcion.getId());
            item.put("nombre", opcion.getNombre());
            item.put("precio", opcion.getPrecioAdicional() == null
                    ? BigDecimal.ZERO : opcion.getPrecioAdicional());
            item.put("grupoId", grupo.getId());
            item.put("grupoNombre", grupo.getNombre());
            detalle.add(item);
        }

        for (ProductoOpcionGrupo grupo : grupos) {
            List<ProductoOpcion> elegidas = seleccionPorGrupo.getOrDefault(grupo.getId(), List.of());
            if (!grupo.isPermiteMultiple() && elegidas.size() > 1) {
                throw new IllegalArgumentException(
                        "El grupo " + grupo.getNombre() + " permite seleccionar una sola opción.");
            }
            if (grupo.isObligatorio() && elegidas.isEmpty()) {
                throw new IllegalArgumentException(
                        "Debe seleccionar una opción en el grupo " + grupo.getNombre() + ".");
            }
        }

        String nombres = detalle.stream()
                .map(item -> String.valueOf(item.get("nombre")))
                .collect(Collectors.joining(", "));

        return new SeleccionOpciones(adicional, serializarDetalle(detalle),
                seleccionadas, nombres);
    }

    public List<Map<String, Object>> leerDetalle(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializarDetalle(List<Map<String, Object>> detalle) {
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible registrar las opciones del producto.");
        }
    }

    public record SeleccionOpciones(BigDecimal adicional,
                                    String detalle,
                                    List<UUID> ids,
                                    String nombres) {
    }
}
