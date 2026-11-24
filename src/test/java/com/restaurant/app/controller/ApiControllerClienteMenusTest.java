package com.restaurant.app.controller;

import com.restaurant.app.model.Cliente;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.services.ConfiguracionImpuestoService;
import com.restaurant.app.services.MenuService;
import com.restaurant.app.services.MesaService;
import com.restaurant.app.services.SesionMesaService;
import com.restaurant.app.services.UserService;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerClienteMenusTest {

    private MockMvc mockMvc;
    private RestauranteRepository restauranteRepository;

    @BeforeEach
    void configurar() {
        restauranteRepository = mock(RestauranteRepository.class);
        ApiController controller = new ApiController(
                mock(UserService.class),
                mock(SesionMesaService.class),
                mock(MenuService.class),
                mock(MesaService.class),
                restauranteRepository,
                mock(SucursalRepository.class),
                mock(ConfiguracionImpuestoService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void devuelveSlugsDeMenusActivosSoloAlClienteAutenticado() throws Exception {
        Restaurante restaurante = new Restaurante();
        restaurante.setNombre("Restaurante Norte");
        restaurante.setSlug("restaurante-norte");
        restaurante.setActivo(true);
        when(restauranteRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(restaurante));
        Cliente cliente = new Cliente();
        cliente.setRol("CLIENTE");

        mockMvc.perform(get("/api/clientes/menus")
                        .sessionAttr("currentUser", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("restaurante-norte"))
                .andExpect(jsonPath("$.ruta").value("/menu/restaurante-norte"))
                .andExpect(jsonPath("$.menus[0].restaurante").value("Restaurante Norte"));
    }

    @Test
    void deniegaMenusAUsuariosQueNoSonClientes() throws Exception {
        com.restaurant.app.model.Administrador admin = new com.restaurant.app.model.Administrador();
        admin.setRol("ADMINISTRADOR");

        mockMvc.perform(get("/api/clientes/menus").sessionAttr("currentUser", admin))
                .andExpect(status().isForbidden());
    }
}
