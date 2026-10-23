package com.inventarioclean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de contrato del esquema.
 *
 * <p>Verifica que Flyway crea las tablas y, sobre todo, que las restricciones
 * de integridad del negocio funcionan: no se puede tener mas disponible que
 * total, ni cantidad negativa, ni un rol o estado fuera del catalogo.
 *
 * <p>Estas reglas son el corazon del sistema: si un CHECK no esta en la base,
 * un bug en la aplicacion dejaria el stock corrupto y habria que reconstruirlo
 * a mano, que es justo lo que este sistema viene a evitar.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Esquema y restricciones de integridad")
class EsquemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Flyway crea las cinco tablas del modelo")
    void seCreanTodasLasTablas() throws Exception {
        List<String> tablas = new ArrayList<>();
        try (Connection con = dataSource.getConnection();
             ResultSet rs = con.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) tablas.add(rs.getString("TABLE_NAME").toLowerCase());
        }

        assertThat(tablas).contains("usuario", "producto", "renta", "movimiento", "auditoria");
    }

    @Test
    @DisplayName("No se puede guardar un producto con mas disponible que total")
    void disponibleNoPuedeSuperarTotal() throws Exception {
        // Es la invariante central: nunca se puede rentar o vender mas de lo
        // que hay. La base la hace cumplir, no solo la aplicacion.
        String error = intentarInsertarProducto(10, 20, "VENTA", "EN_BODEGA");
        assertThat(error).contains("cantidad_disponible");
    }

    @Test
    @DisplayName("No se puede guardar un producto con cantidad negativa")
    void cantidadNegativaNoPermitida() throws Exception {
        String error = intentarInsertarProducto(5, -1, "VENTA", "EN_BODEGA");
        assertThat(error).isNotNull();
    }

    @Test
    @DisplayName("No se permite un tipo de comercio fuera del catalogo")
    void tipoComercioDebeSerValido() throws Exception {
        String error = intentarInsertarProducto(5, 5, "REGALO", "EN_BODEGA");
        assertThat(error).contains("tipo_comercio");
    }

    @Test
    @DisplayName("No se permite un estado fuera del catalogo")
    void estadoDebeSerValido() throws Exception {
        String error = intentarInsertarProducto(5, 5, "VENTA", "PERDIDO");
        assertThat(error).contains("estado");
    }

    @Test
    @DisplayName("No se puede insertar un producto sin SKU unico")
    void skuEsUnico() throws Exception {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {

            insertarProducto(st, "SKU-UNICO-1", 5, 5);

            // Segundo producto con el mismo SKU: debe fallar por el UNIQUE.
            try {
                insertarProducto(st, "SKU-UNICO-1", 1, 1);
                throw new AssertionError("Se deberia haber rechazado el SKU duplicado");
            } catch (Exception e) {
                assertThat(rootMessage(e)).contains("uq_producto_sku");
            }
        }
    }

    private String intentarInsertarProducto(int total, int disponible,
                                            String tipo, String estado) {
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            insertarProducto(st, "SKU-TEST-" + System.nanoTime(), total, disponible, tipo, estado);
            return null;
        } catch (Exception e) {
            return rootMessage(e);
        }
    }

    private void insertarProducto(Statement st, String sku, int total, int disponible)
            throws Exception {
        insertarProducto(st, sku, total, disponible, "VENTA", "EN_BODEGA");
    }

    private void insertarProducto(Statement st, String sku, int total, int disponible,
                                  String tipo, String estado) throws Exception {
        st.executeUpdate("""
                INSERT INTO producto (sku, nombre, cantidad_total, cantidad_disponible,
                                      precio, tipo_comercio, estado, stock_minimo, activo)
                VALUES ('%s', 'Producto de prueba', %d, %d, 100.00, '%s', '%s', 0, TRUE)
                """.formatted(sku, total, disponible, tipo, estado));
    }

    /** Baja hasta el mensaje mas interno de la excepcion. */
    private String rootMessage(Throwable e) {
        Throwable actual = e;
        while (actual.getCause() != null) actual = actual.getCause();
        return actual.getMessage() == null ? "" : actual.getMessage();
    }
}

