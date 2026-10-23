package com.inventarioclean.movimiento;

import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.producto.EstadoProducto;
import com.inventarioclean.producto.Producto;
import com.inventarioclean.producto.ProductoRepository;
import com.inventarioclean.producto.TipoComercio;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del servicio de movimientos, la unica clase autorizada para cambiar
 * el stock.
 *
 * <p>Concentran la regla central del sistema: nunca se puede sacar mas de
 * lo que hay, y cada salida deja rastro.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Movimientos de stock")
class MovimientoServiceTest {

    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UsuarioPrincipal operador;
    private Producto producto;

    @BeforeEach
    void preparar() {
        movimientoRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario user = usuarioRepository.save(Usuario.builder()
                .nombre("Camila").apellido("Rojas")
                .email("camila@test.local").username("CROJAS")
                .passwordHash(passwordEncoder.encode("clave12345"))
                .rol(Rol.OPERADOR).activo(true)
                .build());
        operador = new UsuarioPrincipal(user);

        producto = productoRepository.save(Producto.builder()
                .sku("SILLA-001").nombre("Silla plegable")
                .cantidadTotal(10).cantidadDisponible(10)
                .precio(new BigDecimal("25000"))
                .tipoComercio(TipoComercio.RENTA)
                .estado(EstadoProducto.EN_BODEGA)
                .stockMinimo(2).activo(true)
                .build());
    }

    @Test
    @DisplayName("Una salida descuenta el disponible")
    void salidaDescuentaDisponible() {
        movimientoService.registrar(producto.getId(), TipoMovimiento.RENTA,
                3, "R-2026-0001", operador);

        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(actualizado.getCantidadDisponible()).isEqualTo(7);
        // El total fisico no cambia: el material sigue siendo de la bodega.
        assertThat(actualizado.getCantidadTotal()).isEqualTo(10);
    }

    @Test
    @DisplayName("Una entrada repone el disponible")
    void entradaReponeDisponible() {
        movimientoService.registrar(producto.getId(), TipoMovimiento.RENTA,
                4, "R-2026-0001", operador);
        movimientoService.registrar(producto.getId(), TipoMovimiento.RETORNO_RENTA,
                -4, "R-2026-0001", operador);

        Producto actualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(actualizado.getCantidadDisponible()).isEqualTo(10);
    }

    @Test
    @DisplayName("No se puede sacar mas de lo que hay")
    void rechazaStockInsuficiente() {
        assertThatThrownBy(() -> movimientoService.registrar(
                producto.getId(), TipoMovimiento.RENTA, 11, "R-2026-0001", operador))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("stock suficiente");

        // Y el stock no debe haber cambiado.
        Producto sinCambios = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(sinCambios.getCantidadDisponible()).isEqualTo(10);
    }

    @Test
    @DisplayName("El mensaje de error dice cuanto hay y cuanto se pide")
    void mensajeDeErrorEsUtil() {
        // El documento pide que el error diga exactamente que paso: el
        // operador tiene que ver de inmediato cuanto puede rentar.
        assertThatThrownBy(() -> movimientoService.registrar(
                producto.getId(), TipoMovimiento.RENTA, 15, "R-2026-0001", operador))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("15")     // lo que se pidio
                .hasMessageContaining("10");    // lo que hay
    }

    @Test
    @DisplayName("No se puede reponer mas de lo que hay en total")
    void rechazaRetornoSobredimensionado() {
        // Si alguien devuelve de mas, el stock diria una cosa que no
        // corresponde con la realidad de la bodega.
        assertThatThrownBy(() -> movimientoService.registrar(
                producto.getId(), TipoMovimiento.RETORNO_RENTA, -3, "R-2026-0001", operador))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("retorno supera");
    }

    @Test
    @DisplayName("Una cantidad de cero se rechaza")
    void rechazaCantidadCero() {
        assertThatThrownBy(() -> movimientoService.registrar(
                producto.getId(), TipoMovimiento.RENTA, 0, "R-2026-0001", operador))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("distinta de cero");
    }

    @Test
    @DisplayName("El estado pasa a ARRENDADO cuando queda material fuera")
    void cambiaEstadoSegunStock() {
        movimientoService.registrar(producto.getId(), TipoMovimiento.RENTA,
                2, "R-2026-0001", operador);

        Producto arrendado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(arrendado.getEstado()).isEqualTo(EstadoProducto.ARRENDADO);

        // Al devolverlo todo, vuelve a estar en bodega.
        movimientoService.registrar(producto.getId(), TipoMovimiento.RETORNO_RENTA,
                -2, "R-2026-0001", operador);

        Producto devuelto = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(devuelto.getEstado()).isEqualTo(EstadoProducto.EN_BODEGA);
    }

    @Test
    @DisplayName("Cada movimiento queda registrado con su usuario y su codigo de renta")
    void registraMovimiento() {
        movimientoService.registrar(producto.getId(), TipoMovimiento.RENTA,
                2, "R-2026-0001", operador);

        var movimientos = movimientoRepository.findByCodigoRentaOrderByCreatedAtAsc("R-2026-0001");

        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getTipo()).isEqualTo(TipoMovimiento.RENTA);
        assertThat(movimientos.get(0).getCantidad()).isEqualTo(2);
        // Se compara el id y no el username: la asociacion es lazy y este test
        // corre fuera de transaccion, donde Hibernate no puede cargarla.
        // El username se verifica en los tests de renta, que si corren
        // dentro de una transaccion.
        assertThat(movimientos.get(0).getUsuario().getId()).isEqualTo(operador.getId());
        assertThat(movimientos.get(0).getCreatedAt()).isNotNull();
    }
}

