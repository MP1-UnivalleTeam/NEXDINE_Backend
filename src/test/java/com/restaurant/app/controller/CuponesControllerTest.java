package com.restaurant.app.controller;

import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.CuponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CuponesControllerTest {

    private CuponService cuponService;
    private CuponesController controller;

    @BeforeEach
    void setUp() {
        cuponService = mock(CuponService.class);
        controller = new CuponesController(cuponService);
    }

    @Test
    void requiereSesionParaAdministrarCupones() {
        var respuesta = controller.listarCupones(new MockHttpSession());

        assertEquals(401, respuesta.getStatusCode().value());
        verify(cuponService, never()).listarAdministracion();
    }

    @Test
    void rechazaRolesSinPermisoAdministrativo() {
        MockHttpSession session = new MockHttpSession();
        Usuario cliente = mock(Usuario.class);
        when(cliente.getRol()).thenReturn("CLIENTE");
        session.setAttribute("currentUser", cliente);

        var respuesta = controller.listarCupones(session);

        assertEquals(403, respuesta.getStatusCode().value());
        verify(cuponService, never()).listarAdministracion();
    }

    @Test
    void administraConRolAutorizado() {
        MockHttpSession session = new MockHttpSession();
        Usuario admin = mock(Usuario.class);
        when(admin.getRol()).thenReturn("ADMINISTRADOR");
        session.setAttribute("currentUser", admin);
        when(cuponService.listarAdministracion()).thenReturn(List.of());

        var respuesta = controller.listarCupones(session);

        assertEquals(200, respuesta.getStatusCode().value());
        verify(cuponService).listarAdministracion();
    }
}
