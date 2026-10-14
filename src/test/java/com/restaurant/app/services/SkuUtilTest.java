package com.restaurant.app.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SkuUtil - formato del SKU y normalización")
class SkuUtilTest {


    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Estación Brasa,       EB",
            "Estacion Brasa,       EB",
            "Estación Brasa Sabor, EBS",
            "El Buen Sabor,        EBS",
            "Brasa,                B",
            "Punto,                P",
            "DONDE PEDRO,          DP",
            "La Esquina,           LE"
    })
    @DisplayName("Genera iniciales de nombres con y sin tildes")
    void generaIniciales(String nombre, String esperado) {
        assertEquals(esperado, SkuUtil.generarIniciales(nombre));
    }

    @Test
    @DisplayName("Los artículos y preposiciones aportan su inicial")
    void articulosYPreposiciones() {
        assertEquals("EBS", SkuUtil.generarIniciales("El Buen Sabor"));
        assertEquals("DB", SkuUtil.generarIniciales("De Barcelona"));
        assertEquals("CLS", SkuUtil.generarIniciales("Casa La Salsa"));
    }

    @Test
    @DisplayName("Nombres de una sola palabra")
    void unaSolaPalabra() {
        assertEquals("B", SkuUtil.generarIniciales("Brasa"));
        assertEquals("C", SkuUtil.generarIniciales("Café"));
        assertEquals("P", SkuUtil.generarIniciales("  Postal  "));
    }

    @Test
    @DisplayName("Espacios múltiples y caracteres especiales")
    void espaciosYCaracteres() {
        assertEquals("EB", SkuUtil.generarIniciales("  Estación   Brasa  "));
        assertEquals("EB", SkuUtil.generarIniciales("Estación-Brasa"));
        assertEquals("EB", SkuUtil.generarIniciales("Estación, Brasa."));
        assertEquals("EB", SkuUtil.generarIniciales("Estación & Brasa"));
    }

    @Test
    @DisplayName("Máximo de 5 iniciales")
    void maximoIniciales() {
        assertEquals("ABCDE", SkuUtil.generarIniciales("Alpha Bravo Charlie Delta Echo Foxtrot"));
    }

    @Test
    @DisplayName("Nombres sin caracteres válidos devuelven GEN")
    void nombresSinValidos() {
        assertEquals("GEN", SkuUtil.generarIniciales("***"));
        assertEquals("GEN", SkuUtil.generarIniciales("   "));
        assertEquals("GEN", SkuUtil.generarIniciales(null));
    }

    @Test
    @DisplayName("Las iniciales son deterministas")
    void esDeterminista() {
        String a = SkuUtil.generarIniciales("Estación Brasa");
        String b = SkuUtil.generarIniciales("Estación Brasa");
        assertEquals(a, b);
    }


    @ParameterizedTest(name = "id {0} -> {1}")
    @CsvSource({"1, R001", "2, R002", "7, R007", "42, R042", "123, R123", "1000, R1000"})
    @DisplayName("El código se deriva del numero de secuencia, con ceros a la izquierda")
    void codigoDesdeId(Long id, String esperado) {
        assertEquals(esperado, SkuUtil.formatearCodigo(id));
    }

    @Test
    @DisplayName("El código NO se trunca cuando el id es largo")
    void codigoNoSeTrunca() {
        assertEquals("R1234567", SkuUtil.formatearCodigo(1234567L));
    }

    @Test
    @DisplayName("Números de secuencia inválidos se rechazan")
    void numeroSecuenciaInvalido() {
        assertThrows(IllegalArgumentException.class, () -> SkuUtil.formatearCodigo(0L));
        assertThrows(IllegalArgumentException.class, () -> SkuUtil.formatearCodigo(0L));
        assertThrows(IllegalArgumentException.class, () -> SkuUtil.formatearCodigo(-1L));
    }

    @Test
    @DisplayName("Números distintos producen códigos distintos (unicidad por construcción)")
    void codigosUnicos() {
        assertTrue(!SkuUtil.formatearCodigo(1L).equals(SkuUtil.formatearCodigo(2L)));
        assertTrue(!SkuUtil.formatearCodigo(7L).equals(SkuUtil.formatearCodigo(70L)));
    }


    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"1, 000001", "9, 000009", "99, 000099", "999, 000999",
            "9999, 009999", "99999, 099999", "999999, 999999"})
    @DisplayName("El consecutivo usa al menos 6 dígitos con ceros a la izquierda")
    void consecutivoConCeros(long valor, String esperado) {
        assertEquals(esperado, SkuUtil.formatearConsecutivo(valor));
    }

    @Test
    @DisplayName("Al superar 999999 amplía la longitud sin truncar")
    void consecutivoSinTruncar() {
        assertEquals("1000000", SkuUtil.formatearConsecutivo(1000000L));
        assertEquals("1000001", SkuUtil.formatearConsecutivo(1000001L));
        assertEquals("9999999999", SkuUtil.formatearConsecutivo(9999999999L));
    }

    @Test
    @DisplayName("Consecutivos no positivos se rechazan")
    void consecutivoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> SkuUtil.formatearConsecutivo(0L));
        assertThrows(IllegalArgumentException.class, () -> SkuUtil.formatearConsecutivo(-1L));
    }


    @Test
    @DisplayName("Construye el SKU con el formato definido")
    void construyeSku() {
        assertEquals("EB-R001-000001", SkuUtil.construirSku("EB", "R001", 1));
        assertEquals("EBS-R002-000002", SkuUtil.construirSku("EBS", "R002", 2));
        assertEquals("EBS-R003-000001", SkuUtil.construirSku("EBS", "R003", 1));
    }

    @Test
    @DisplayName("El SKU cumple la expresión regular INICIALES-CODIGO-CONSECUTIVO")
    void formatoSku() {
        String sku = SkuUtil.construirSku("EBS", "R002", 42);
        assertTrue(sku.matches("^[A-Z0-9]{1,5}-R[0-9]{3,}-[0-9]{6,}$"),
                "SKU con formato inesperado: " + sku);
    }

    @Test
    @DisplayName("Restaurantes con las MISMAS iniciales no colisionan")
    void inicialesRepetidasNoColisionan() {
        String a = SkuUtil.construirSku(SkuUtil.generarIniciales("Estación Brasa Sabor"), "R002", 1);
        String b = SkuUtil.construirSku(SkuUtil.generarIniciales("El Buen Sabor"), "R003", 1);

        assertEquals("EBS-R002-000001", a);
        assertEquals("EBS-R003-000001", b);
        assertTrue(!a.equals(b), "El código del restaurante debe desambiguar");
    }

    @Test
    @DisplayName("SKU con datos incompletos se rechaza")
    void skuIncompleto() {
        assertThrows(IllegalArgumentException.class,
                () -> SkuUtil.construirSku(null, "R001", 1));
        assertThrows(IllegalArgumentException.class,
                () -> SkuUtil.construirSku("EB", null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SkuUtil.construirSku("", "R001", 1));
    }

    @Test
    @DisplayName("El código del restaurante se normaliza a mayúsculas")
    void codigoNormalizado() {
        assertEquals("EB-R001-000001", SkuUtil.construirSku("EB", "r001", 1));
    }

    @Test
    @DisplayName("Extrae el consecutivo de un SKU existente")
    void extraeConsecutivo() {
        assertEquals(1L, SkuUtil.extraerConsecutivo("EB-R001-000001"));
        assertEquals(42L, SkuUtil.extraerConsecutivo("EBS-R002-000042"));
        assertEquals(1000000L, SkuUtil.extraerConsecutivo("EBS-R002-1000000"));
        assertNull(SkuUtil.extraerConsecutivo(null));
        assertNull(SkuUtil.extraerConsecutivo("SKU_SIN_SEPARADOR"));
    }


    @Test
    @DisplayName("Nombres equivalentes del requerimiento se normalizan igual")
    void normalizaEquivalentes() {
        String esperado = "hamburguesa clasica";

        assertEquals(esperado, SkuUtil.normalizarNombre("Hamburguesa Clásica"));
        assertEquals(esperado, SkuUtil.normalizarNombre(" hamburguesa clásica "));
        assertEquals(esperado, SkuUtil.normalizarNombre("HAMBURGUESA CLASICA"));
        assertEquals(esperado, SkuUtil.normalizarNombre("Hamburguesa   Clásica"));
        assertEquals(esperado, SkuUtil.normalizarNombre("\tHamburguesa\n Clásica  "));
    }

    @Test
    @DisplayName("La normalización es idempotente")
    void normalizacionIdempotente() {
        String una = SkuUtil.normalizarNombre("  Bandeja   PAISA  ");
        assertEquals(una, SkuUtil.normalizarNombre(una));
    }

    @Test
    @DisplayName("Nombres realmente distintos producen claves distintas")
    void normalizaDistintos() {
        String a = SkuUtil.normalizarNombre("Hamburguesa Clásica");
        String b = SkuUtil.normalizarNombre("Hamburgusa Doble");

        assertTrue(!a.equals(b));
    }

    @Test
    @DisplayName("Guiones y puntuación se convierten en espacio")
    void normalizaSimbolos() {
        assertEquals("pizza napolitana", SkuUtil.normalizarNombre("Pizza-Napolitana"));
        assertEquals("pizza napolitana", SkuUtil.normalizarNombre("Pizza, Napolitana."));
        assertEquals("jumbo 2", SkuUtil.normalizarNombre("Jumbo  2"));
    }

    @Test
    @DisplayName("Nombres vacíos o nulos se normalizan a null")
    void normalizaVacios() {
        assertNull(SkuUtil.normalizarNombre(null));
        assertNull(SkuUtil.normalizarNombre(""));
        assertNull(SkuUtil.normalizarNombre("     "));
    }
}
