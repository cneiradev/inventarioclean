package com.inventarioclean.producto;

import com.inventarioclean.auditoria.AuditoriaRepository;
import com.inventarioclean.movimiento.MovimientoRepository;
import com.inventarioclean.renta.RentaRepository;
import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.exception.RecursoNoEncontradoException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.producto.dto.ProductoRequest;
import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la gestion de productos.
 *
 * <p>El foco esta en las reglas que el documento no especifica pero que
 * definen el valor del sistema: el SKU se normaliza y es unico, al crear
 * todo el stock queda disponible, no se puede bajar el total por debajo de
 * lo ya reservado, el SKU no se cambia, y eliminar es soft delete para no
 * perder el historial.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Gestion de productos")
class ProductoServiceTest {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private RentaRepository rentaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UsuarioPrincipal auditor;

    @BeforeEach
    void preparar() {
        // El orden importa: primero los hijos (movimientos y rentas) y despues
        // producto y usuario. Al revÃ©s, la base rechaza el borrado por las
        // claves foraneas.
        movimientoRepository.deleteAll();
        rentaRepository.deleteAll();
        productoRepository.deleteAll();
        auditoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin").apellido("Sistema")
                .email("admin@test.local").username("ADMIN")
                .passwordHash(passwordEncoder.encode("admin123"))
                .rol(Rol.ADMINISTRADOR).activo(true)
                .build());

        auditor = new UsuarioPrincipal(admin);
    }

    private ProductoRequest request(String sku, String nombre, int cantidad,
                                    BigDecimal precio, TipoComercio tipo) {
        ProductoRequest r = new ProductoRequest();
        r.setSku(sku);
        r.setNombre(nombre);
        r.setMedidas("2x3 m");
        r.setCantidadTotal(cantidad);
        r.setPrecio(precio);
        r.setTipoComercio(tipo);
        r.setStockMinimo(0);
        return r;
    }

@Test
    @DisplayName("Crea un producto con todo el stock disponible")
    void creaProducto() {
        var creado = productoService.crear(
                request("silla-001", "Silla plegable", 10,
                        new BigDecimal("25000.50"), TipoComercio.VENTA),
                auditor);

        assertThat(creado.sku()).isEqualTo("SILLA-001");
        assertThat(creado.cantidadTotal()).isEqualTo(10);
        assertThat(creado.cantidadDisponible()).isEqualTo(10);
        assertThat(creado.estado()).isEqualTo(EstadoProducto.EN_BODEGA);
        assertThat(creado.precio()).isEqualByComparingTo("25000.50");
    }

    @Test
    @DisplayName("El SKU se normaliza a mayusculas y no se repite")
    void normalizaYUnificaSku() {
        productoService.crear(
                request("mesa-01", "Mesa", 1, BigDecimal.ONE, TipoComercio.VENTA), auditor);

        // Mismo SKU en otro caso: es el mismo producto, no uno nuevo.
        assertThatThrownBy(() -> productoService.crear(
                request("MESA-01", "Mesa otra", 1, BigDecimal.ONE, TipoComercio.VENTA),
                auditor))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("MESA-01");
    }

    @Test
    @DisplayName("Busca por SKU aceptando minusculas")
    void buscaPorSku() {
        productoService.crear(
                request("silla-001", "Silla", 5, BigDecimal.TEN, TipoComercio.RENTA), auditor);

        assertThat(productoService.buscarPorSku("silla-001").nombre()).isEqualTo("Silla");
        assertThat(productoService.buscarPorSku("SILLA-001").nombre()).isEqualTo("Silla");
    }

    @Test
    @DisplayName("Error claro con el SKU que se busco y no existe")
    void errorSkuInexistente() {
        assertThatThrownBy(() -> productoService.buscarPorSku("NO-EXISTE"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("NO-EXISTE");
    }

    @Test
    @DisplayName("No deja bajar el total por debajo de lo ya reservado")
    void noPermiteTotalMenorQueLoReservado() {
        var creado = productoService.crear(
                request("silla-001", "Silla", 10, BigDecimal.TEN, TipoComercio.RENTA), auditor);

        // Se simula que 6 unidades ya salieron de bodega.
        Producto producto = productoRepository.findById(creado.id()).orElseThrow();
        producto.setCantidadDisponible(4);
        productoRepository.save(producto);

        ProductoRequest update = request("SILLA-001", "Silla", 5,
                BigDecimal.TEN, TipoComercio.RENTA);

        // 5 en total con 6 reservadas es imposible: dejaria disponible < 0.
        assertThatThrownBy(() -> productoService.actualizar("SILLA-001", update, auditor))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("rentadas");
    }

    @Test
    @DisplayName("Al subir el total, el disponible sube manteniendo lo reservado")
    void totalAumentaYDisponibleAcompana() {
        var creado = productoService.crear(
                request("silla-001", "Silla", 10, BigDecimal.TEN, TipoComercio.RENTA), auditor);

        Producto producto = productoRepository.findById(creado.id()).orElseThrow();
        producto.setCantidadDisponible(4);      // 6 reservadas
        productoRepository.save(producto);

        var actualizado = productoService.actualizar("SILLA-001",
                request("SILLA-001", "Silla", 15, BigDecimal.TEN, TipoComercio.RENTA),
                auditor);

        assertThat(actualizado.cantidadTotal()).isEqualTo(15);
        // 15 - 6 reservadas = 9 disponibles, no 15.
        assertThat(actualizado.cantidadDisponible()).isEqualTo(9);
    }

    @Test
    @DisplayName("No se puede cambiar el SKU de un producto existente")
    void noPermiteCambiarSku() {
        productoService.crear(
                request("silla-001", "Silla", 3, BigDecimal.TEN, TipoComercio.VENTA), auditor);

        assertThatThrownBy(() -> productoService.actualizar("SILLA-001",
                request("silla-999", "Silla", 3, BigDecimal.TEN, TipoComercio.VENTA), auditor))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("SKU");
    }
@Test
    @DisplayName("Marca stock bajo segun el minimo configurado")
    void detectaStockBajo() {
        var conMinimo = productoService.crear(
                request("silla-001", "Silla", 3, BigDecimal.TEN, TipoComercio.VENTA),
                auditor);
        assertThat(conMinimo.stockBajo()).isFalse();   // minimo 0, hay 3

        ProductoRequest conUmbral = request("SILLA-002", "Mesa", 2,
                BigDecimal.TEN, TipoComercio.VENTA);
        conUmbral.setStockMinimo(5);      // minimo 5 pero hay 2
        var enAlerta = productoService.crear(conUmbral, auditor);
        assertThat(enAlerta.stockBajo()).isTrue();
    }

    @Test
    @DisplayName("Eliminar es soft delete: el historial no se pierde")
    void eliminarEsSoftDelete() {
        productoService.crear(
                request("silla-001", "Silla", 5, BigDecimal.TEN, TipoComercio.RENTA), auditor);

        productoService.eliminar("SILLA-001", auditor);

        // La fila sigue existiendo...
        Producto enBase = productoRepository.findBySku("SILLA-001").orElseThrow();
        assertThat(enBase.isActivo()).as("debe quedar inactivo").isFalse();

        // ...pero ya no aparece en el inventario.
        assertThatThrownBy(() -> productoService.buscarPorSku("SILLA-001"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Filtra el listado por tipo de comercio")
    void filtraPorTipo() {
        productoService.crear(
                request("a-01", "Producto venta", 5, BigDecimal.TEN, TipoComercio.VENTA), auditor);
        productoService.crear(
                request("b-01", "Producto renta", 5, BigDecimal.TEN, TipoComercio.RENTA), auditor);

        var soloVenta = productoService.listar(TipoComercio.VENTA, null, false,
                PageRequest.of(0, 20));

        assertThat(soloVenta.getTotalElements()).isEqualTo(1);
        assertThat(soloVenta.getContent().get(0).sku()).isEqualTo("A-01");

        var ambos = productoService.listar(null, null, false, PageRequest.of(0, 20));
        assertThat(ambos.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Busca por texto parcial sobre nombre y SKU")
    void buscaPorTexto() {
        productoService.crear(
                request("silla-001", "Silla plegable", 5, BigDecimal.TEN, TipoComercio.VENTA),
                auditor);
        productoService.crear(
                request("mesa-01", "Mesa de madera", 5, BigDecimal.TEN, TipoComercio.VENTA),
                auditor);

        var porNombre = productoService.listar(null, "silla", false, PageRequest.of(0, 20));
        assertThat(porNombre.getTotalElements()).isEqualTo(1);

        var porSku = productoService.listar(null, "MESA", false, PageRequest.of(0, 20));
        assertThat(porSku.getContent().get(0).sku()).isEqualTo("MESA-01");
    }

    @Test
    @DisplayName("El dashboard devuelve solo los productos en stock bajo")
    void dashboardStockBajo() {
        ProductoRequest conUmbral = request("silla-001", "Silla", 2,
                BigDecimal.TEN, TipoComercio.VENTA);
        conUmbral.setStockMinimo(5);
        productoService.crear(conUmbral, auditor);
        productoService.crear(
                request("mesa-01", "Mesa", 10, BigDecimal.TEN, TipoComercio.VENTA), auditor);

        List<?> bajos = productoService.stockBajo(TipoComercio.VENTA);

        assertThat(bajos).hasSize(1);
    }
}

