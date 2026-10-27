package com.restaurant.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.app.model.Cupon;
import com.restaurant.app.model.Producto;
import com.restaurant.app.model.ProductoOpcion;
import com.restaurant.app.model.ProductoOpcionGrupo;
import com.restaurant.app.model.ProductoSucursal;
import com.restaurant.app.model.Restaurante;
import com.restaurant.app.model.Sucursal;
import com.restaurant.app.repository.CuponRepository;
import com.restaurant.app.repository.ProductoOpcionGrupoRepository;
import com.restaurant.app.repository.ProductoOpcionRepository;
import com.restaurant.app.repository.ProductoRepository;
import com.restaurant.app.repository.ProductoSucursalRepository;
import com.restaurant.app.repository.RestauranteRepository;
import com.restaurant.app.repository.SucursalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PedidoInvitadoIntegracionTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private RestauranteRepository restaurantes;
    @Autowired
    private SucursalRepository sucursales;
    @Autowired
    private ProductoRepository productos;
    @Autowired
    private ProductoSucursalRepository productoSucursales;
    @Autowired
    private ProductoOpcionGrupoRepository grupos;
    @Autowired
    private ProductoOpcionRepository opciones;
    @Autowired
    private CuponRepository cupones;
    @Autowired
    private ObjectMapper mapper;

    private ResponseEntity<String> postForm(String ruta, MultiValueMap<String, String> datos) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return rest.postForEntity(ruta, new HttpEntity<>(datos, cabeceras), String.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flujoInvitadoCompletoConOpcionesCuponYPropina() throws Exception {
        Restaurante r = new Restaurante();
        r.setNombre("Restaurant IT");
        restaurantes.save(r);

        Sucursal s = new Sucursal();
        s.setRestauranteId(r.getId());
        s.setNombre("Sucursal Norte");
        sucursales.save(s);

        Producto p = new Producto();
        p.setNombre("Hamburguesa");
        p.setPrecio(new BigDecimal("10.00"));
        p.setRestauranteId(r.getId());
        productos.save(p);

        ProductoSucursal link = new ProductoSucursal(p.getId(), s.getId(), r.getId());
        productoSucursales.save(link);

        ProductoOpcionGrupo g = new ProductoOpcionGrupo();
        g.setProductoId(p.getId());
        g.setNombre("Extras");
        g.setPermiteMultiple(true);
        grupos.save(g);

        ProductoOpcion o1 = new ProductoOpcion();
        o1.setGrupoId(g.getId());
        o1.setNombre("Queso");
        o1.setPrecioAdicional(new BigDecimal("2.50"));
        opciones.save(o1);

        Cupon c = new Cupon();
        c.setCodigo("TEST10");
        c.setTipo("PORCENTAJE");
        c.setValor(new BigDecimal("10"));
        cupones.save(c);

        MultiValueMap<String, String> creacion = new LinkedMultiValueMap<>();
        creacion.add("nombre", "Cliente Prueba");
        creacion.add("telefono", "3001234567");
        creacion.add("tipoServicio", "DOMICILIO");
        creacion.add("direccion", "Calle 1");
        creacion.add("sucursalId", s.getId().toString());
        ResponseEntity<String> respuestaCreacion = postForm("/api/pedidos/invitado", creacion);
        assertEquals(200, respuestaCreacion.getStatusCode().value());
        Map<String, Object> pedidoCreado = mapper.readValue(respuestaCreacion.getBody(), Map.class);
        UUID pedidoId = UUID.fromString(String.valueOf(pedidoCreado.get("id")));
        assertNotNull(pedidoCreado.get("numeroSeguimiento"));

        ResponseEntity<String> opcionesProducto =
                rest.getForEntity("/api/productos/" + p.getId() + "/opciones", String.class);
        assertEquals(200, opcionesProducto.getStatusCode().value());
        List<Object> gruposOpciones = mapper.readValue(opcionesProducto.getBody(), List.class);
        assertEquals(1, gruposOpciones.size());

        MultiValueMap<String, String> item = new LinkedMultiValueMap<>();
        item.add("productoId", p.getId().toString());
        item.add("cantidad", "2");
        item.add("opcionIds", o1.getId().toString());
        ResponseEntity<String> respuestaItem = postForm(
                "/api/pedidos/" + pedidoId + "/items", item);
        assertEquals(200, respuestaItem.getStatusCode().value());

        MultiValueMap<String, String> opcionInvalida = new LinkedMultiValueMap<>();
        opcionInvalida.add("productoId", p.getId().toString());
        opcionInvalida.add("cantidad", "1");
        opcionInvalida.add("opcionIds", UUID.randomUUID().toString());
        assertEquals(400, postForm("/api/pedidos/" + pedidoId + "/items", opcionInvalida)
                .getStatusCode().value());

        MultiValueMap<String, String> validar = new LinkedMultiValueMap<>();
        validar.add("codigo", "TEST10");
        validar.add("subtotal", "25.00");
        ResponseEntity<String> respuestaCupon = postForm("/api/cupones/validar", validar);
        assertEquals(200, respuestaCupon.getStatusCode().value());
        Map<String, Object> cuponValidado = mapper.readValue(respuestaCupon.getBody(), Map.class);
        assertEquals(0, new BigDecimal("2.50").compareTo(
                new BigDecimal(String.valueOf(cuponValidado.get("descuento")))));

        MultiValueMap<String, String> confirmacion = new LinkedMultiValueMap<>();
        confirmacion.add("propinaPorcentaje", "10");
        confirmacion.add("cuponCodigo", "TEST10");
        ResponseEntity<String> respuestaConfirmacion = postForm(
                "/api/pedidos/" + pedidoId + "/confirmar", confirmacion);
        assertEquals(200, respuestaConfirmacion.getStatusCode().value());
        Map<String, Object> resumen = mapper.readValue(respuestaConfirmacion.getBody(), Map.class);

        assertEquals("CONFIRMADO", resumen.get("estado"));
        assertEquals(0, new BigDecimal("25.00").compareTo(
                new BigDecimal(String.valueOf(resumen.get("subtotal")))));
        assertEquals(0, new BigDecimal("2.50").compareTo(
                new BigDecimal(String.valueOf(resumen.get("descuento")))));
        assertEquals(0, new BigDecimal("2.50").compareTo(
                new BigDecimal(String.valueOf(resumen.get("propina")))));
        assertEquals(0, new BigDecimal("0.00").compareTo(
                new BigDecimal(String.valueOf(resumen.get("impuestos")))));
        assertEquals(0, new BigDecimal("25.00").compareTo(
                new BigDecimal(String.valueOf(resumen.get("total")))));
        assertEquals("TEST10", resumen.get("cuponCodigo"));

        List<Object> items = (List<Object>) resumen.get("items");
        Map<String, Object> primerItem = (Map<String, Object>) items.get(0);
        List<Object> personalizaciones = (List<Object>) primerItem.get("personalizaciones");
        assertTrue(personalizaciones.contains("Queso"));
        assertEquals(0, new BigDecimal("12.50").compareTo(
                new BigDecimal(String.valueOf(primerItem.get("precioUnitario")))
                        .add(new BigDecimal(String.valueOf(primerItem.get("adicionalOpciones"))))));

        ResponseEntity<String> resumenFinal =
                rest.getForEntity("/api/pedidos/" + pedidoId + "/resumen", String.class);
        assertEquals(200, resumenFinal.getStatusCode().value());
        Map<String, Object> resumenRecargado =
                mapper.readValue(resumenFinal.getBody(), Map.class);
        assertEquals(0, new BigDecimal("25.00").compareTo(
                new BigDecimal(String.valueOf(resumenRecargado.get("total")))));

        ResponseEntity<String> noEncontrado =
                rest.getForEntity("/api/pedidos/" + UUID.randomUUID() + "/resumen", String.class);
        assertEquals(404, noEncontrado.getStatusCode().value());
    }
}
