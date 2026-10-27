package com.restaurant.app.controller;

import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;
import com.restaurant.app.services.MenuService;
import com.restaurant.app.services.MesaService;
import com.restaurant.app.services.SesionMesaService;
import com.restaurant.app.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiControllerMenuPublicoTest {

    private UserService userService;
    private SesionMesaService sesionMesaService;
    private MenuService menuService;
    private MesaService mesaService;
    private RestauranteRepository restauranteRepository;
    private SucursalRepository sucursalRepository;
    private ApiController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        sesionMesaService = mock(SesionMesaService.class);
        menuService = mock(MenuService.class);
        mesaService = mock(MesaService.class);
        restauranteRepository = mock(RestauranteRepository.class);
        sucursalRepository = mock(SucursalRepository.class);
        controller = new ApiController(
                userService,
                sesionMesaService,
                menuService,
                mesaService,
                restauranteRepository,
                sucursalRepository,
                BigDecimal.ZERO
        );
    }

    @Test
    void conservaMenuPublicoGlobalCuandoNoSeSeleccionaSucursal() {
        UUID restauranteId = UUID.randomUUID();
        Restaurante restaurante = restaurante(restauranteId);
        Sucursal sucursal = sucursal(UUID.randomUUID(), restauranteId, true);
        List<Map<String, Object>> categorias = List.of(Map.of("nombre", "Platos"));

        when(restauranteRepository.findBySlugAndActivoTrue("casa")).thenReturn(Optional.of(restaurante));
        when(sucursalRepository.findByRestauranteIdAndActivaTrue(restauranteId)).thenReturn(List.of(sucursal));
        when(menuService.getMenuPublicoPorRestaurante(restauranteId)).thenReturn(Map.of("categorias", categorias));

        ResponseEntity<?> respuesta = controller.menuPublico("casa", null);

        assertEquals(200, respuesta.getStatusCode().value());
        assertSame(categorias, ((Map<?, ?>) respuesta.getBody()).get("menu"));
        verify(menuService, never()).getMenuPorSucursal(sucursal.getId());
    }

    @Test
    void filtraMenuPorSucursalActivaDelRestaurante() {
        UUID restauranteId = UUID.randomUUID();
        UUID sucursalId = UUID.randomUUID();
        Restaurante restaurante = restaurante(restauranteId);
        Sucursal sucursal = sucursal(sucursalId, restauranteId, true);
        List<Map<String, Object>> categorias = List.of(Map.of("nombre", "Platos"));

        when(restauranteRepository.findBySlugAndActivoTrue("casa")).thenReturn(Optional.of(restaurante));
        when(sucursalRepository.findByRestauranteIdAndActivaTrue(restauranteId)).thenReturn(List.of(sucursal));
        when(sucursalRepository.findById(sucursalId)).thenReturn(Optional.of(sucursal));
        when(menuService.getMenuPorSucursal(sucursalId)).thenReturn(Map.of("categorias", categorias));

        ResponseEntity<?> respuesta = controller.menuPublico("casa", sucursalId);
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();

        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(sucursalId, cuerpo.get("sucursalId"));
        assertSame(categorias, cuerpo.get("menu"));
        verify(menuService, never()).getMenuPublicoPorRestaurante(restauranteId);
    }

    @Test
    void rechazaSucursalInactivaODeOtroRestaurante() {
        UUID restauranteId = UUID.randomUUID();
        UUID sucursalId = UUID.randomUUID();
        Restaurante restaurante = restaurante(restauranteId);
        Sucursal sucursal = sucursal(sucursalId, UUID.randomUUID(), false);

        when(restauranteRepository.findBySlugAndActivoTrue("casa")).thenReturn(Optional.of(restaurante));
        when(sucursalRepository.findByRestauranteIdAndActivaTrue(restauranteId)).thenReturn(List.of());
        when(sucursalRepository.findById(sucursalId)).thenReturn(Optional.of(sucursal));

        ResponseEntity<?> respuesta = controller.menuPublico("casa", sucursalId);

        assertEquals(404, respuesta.getStatusCode().value());
        verify(menuService, never()).getMenuPorSucursal(sucursalId);
    }

    private Restaurante restaurante(UUID id) {
        Restaurante restaurante = mock(Restaurante.class);
        when(restaurante.getId()).thenReturn(id);
        when(restaurante.getNombre()).thenReturn("Casa");
        when(restaurante.getSlug()).thenReturn("casa");
        return restaurante;
    }

    private Sucursal sucursal(UUID id, UUID restauranteId, boolean activa) {
        Sucursal sucursal = mock(Sucursal.class);
        when(sucursal.getId()).thenReturn(id);
        when(sucursal.getRestauranteId()).thenReturn(restauranteId);
        when(sucursal.getNombre()).thenReturn("Norte");
        when(sucursal.getDireccion()).thenReturn("Calle 1");
        when(sucursal.getTelefono()).thenReturn("000");
        when(sucursal.isActiva()).thenReturn(activa);
        return sucursal;
    }
}
