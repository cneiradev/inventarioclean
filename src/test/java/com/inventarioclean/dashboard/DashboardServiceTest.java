package com.inventarioclean.dashboard;

import com.inventarioclean.auditoria.AuditoriaRepository;
import com.inventarioclean.movimiento.MovimientoRepository;
import com.inventarioclean.producto.EstadoProducto;
import com.inventarioclean.producto.Producto;
import com.inventarioclean.producto.ProductoRepository;
import com.inventarioclean.producto.TipoComercio;
import com.inventarioclean.renta.RentaRepository;
import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de los indicadores del panel.
 *
 * <p>Un dashboard con numeros que no cuadran es peor que no tenerlo: el
 * administrador decisions sobre esos numeros. Estos tests fijan que cada
 * indicador se calcule sobre lo que realmente hay en la base.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Dashboard")
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private RentaRepository rentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void preparar() {
        movimientoRepository.deleteAll();
        rentaRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();
        auditoriaRepository.deleteAll();

        usuarioRepository.save(Usuario.builder()
                .nombre("Camila").apellido("Rojas")
                .email("camila@test.local").username("CROJAS")
                .passwordHash(passwordEncoder.encode("clave12345"))
                .rol(Rol.OPERADOR).activo(true)
                .build());
    }

    private Producto crear(String sku, String nombre, int total, int minimo,
                           TipoComercio tipo, int disponible) {
        return productoRepository.save(Producto.builder()
                .sku(sku).nombre(nombre)
                .cantidadTotal(total).cantidadDisponible(disponible)
                .precio(new BigDecimal("10000"))
                .tipoComercio(tipo)
                .estado(EstadoProducto.EN_BODEGA)
                .stockMinimo(minimo).activo(true)
                .build());
    }

    @Test
    @DisplayName("Cuenta los productos por tipo de comercio")
    void cuentaPorTipo() {
        crear("A-1", "Silla", 10, 2, TipoComercio.RENTA, 10);
        crear("B-1", "Mesa", 5, 1, TipoComercio.VENTA, 5);
        crear("C-1", "Sillon", 3, 1, TipoComercio.RENTA, 3);

        var d = dashboardService.obtener();

        assertThat(d.totalProductos()).isEqualTo(3);
        assertThat(d.productosVenta()).isEqualTo(1);
        assertThat(d.productosRenta()).isEqualTo(2);
    }

    @Test
    @DisplayName("Detecta los productos en stock bajo, de venta y de renta")
    void detectaStockBajo() {
        // 2 disponibles con minimo 5: bajo.
        crear("A-1", "Silla", 10, 5, TipoComercio.RENTA, 2);
        // 10 con minimo 2: suficiente.
        crear("B-1", "Mesa", 10, 2, TipoComercio.VENTA, 10);

        var d = dashboardService.obtener();

        assertThat(d.stockBajo()).isEqualTo(1);
        assertThat(d.productosStockBajo()).hasSize(1);
        assertThat(d.productosStockBajo().get(0).sku()).isEqualTo("A-1");
    }

    @Test
    @DisplayName("El disponible y lo reservado cuadran con el total")
    void sumaDisponibleYReservado() {
        // 20 en total, 16 disponibles -> 4 reservados (rentadas).
        crear("A-1", "Silla", 20, 2, TipoComercio.RENTA, 16);
        crear("B-1", "Mesa", 5, 1, TipoComercio.VENTA, 5);

        var d = dashboardService.obtener();

        assertThat(d.unidadesDisponibles()).isEqualTo(21);
        assertThat(d.unidadesRentadas()).isEqualTo(4);
    }

    @Test
    @DisplayName("Un producto desactivado no cuenta")
    void ignoraDesactivados() {
        Producto p = crear("A-1", "Silla", 10, 2, TipoComercio.RENTA, 10);
        crear("B-1", "Mesa", 5, 1, TipoComercio.VENTA, 5);

        p.setActivo(false);
        productoRepository.save(p);

        var d = dashboardService.obtener();

        // El conteo por tipo ya filtra por activo; el total es el catalogo vivo.
        assertThat(d.productosRenta()).isEqualTo(0);
        assertThat(d.unidadesDisponibles()).isEqualTo(5);
    }

    @Test
    @DisplayName("Con la base vacia los indicadores dan cero, no null")
    void baseVaciaDaCeros() {
        // Un panel con nulls en vez de ceros rompe la pantalla: se defensa
        // el caso de arranque sin datos.
        var d = dashboardService.obtener();

        assertThat(d.totalProductos()).isZero();
        assertThat(d.stockBajo()).isZero();
        assertThat(d.rentasActivas()).isZero();
        assertThat(d.unidadesDisponibles()).isZero();
        assertThat(d.unidadesRentadas()).isZero();
        assertThat(d.productosStockBajo()).isEmpty();
        assertThat(d.ultimasOperaciones()).isEmpty();
    }
}
