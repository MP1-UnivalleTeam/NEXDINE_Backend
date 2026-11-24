package com.restaurant.app.controller;

import com.restaurant.app.model.Administrador;
import com.restaurant.app.model.Cliente;
import com.restaurant.app.model.Usuario;
import com.restaurant.app.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

class AuthControllerTest {

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void configurar() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService)).build();
    }

    @Test
    void registraClienteConAliasDeContrasenaConEnie() throws Exception {
        Usuario cliente = new Cliente();
        cliente.setNombre("cliente1");
        cliente.setRol("CLIENTE");
        cliente.setEstado(true);

        when(userService.existeNombre("cliente1")).thenReturn(false);
        when(userService.createUser(eq("cliente1"), eq("secreto1"),
                eq("CLIENTE"), any(), any(), eq(null))).thenReturn(cliente);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "cliente1",
                                  "contraseña": "secreto1",
                                  "celular": "3001234567",
                                  "direccion": "Calle 1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("cliente1"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void registraDuenoCreadoComoAdministrador() throws Exception {
        Usuario dueno = new Administrador();
        dueno.setNombre("dueno1");
        dueno.setRol("ADMINISTRADOR");
        dueno.setEstado(true);

        when(userService.existeNombre("dueno1")).thenReturn(false);
        when(userService.createUser(eq("dueno1"), eq("secreto1"),
                eq("ADMINISTRADOR"), any(), any(), eq(null))).thenReturn(dueno);

        mockMvc.perform(post("/api/auth/register/dueño")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "dueno1",
                                  "contraseña": "secreto1",
                                  "celular": "3001234567",
                                  "direccion": "Calle 1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("dueno1"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }
}
