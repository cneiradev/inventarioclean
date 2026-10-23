package com.inventarioclean.movimiento;

import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.exception.RecursoNoEncontradoException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.producto.EstadoProducto;
import com.inventarioclean.producto.Producto;
import com.inventarioclean.producto.ProductoRepository;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica movimientos de stock.
 *
 * <p><b>Esta es la unica clase que puede cambiar {@code cantidad_disponible}.</b>
 * Centralizarlo aqui es lo que hace confiable el inventario: no importa desde
 * donde se llame (una venta, un ajuste), el stock se descuenta o se repone con
 * la misma logica y queda registrado el motivo.
 *
 * <p><b>Por que el signo importa:</b> un movimiento con cantidad positiva
 * saca material de bodega; negativa, lo devuelve. Asi el historial se lee
 * sin tener que interpretar el tipo.
 *
 * <p><b>Concurrencia:</b> el producto se lee con bloqueo pesimista
 * (SELECT FOR UPDATE). Si dos operadores venden el mismo producto a la vez,
 * el segundo espera en vez de validar contra un stock que ya cambio.
 */
@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final ProductoRepository productoRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;

/**
     * Registra un movimiento y actualiza el stock del producto, todo dentro
     * de la misma transaccion.
     *
     * @param productoId  producto afectado
     * @param tipo        motivo del movimiento
     * @param cantidad    con signo: positiva saca, negativa repone
     * @param usuario     quien ejecuta
     * @return el movimiento guardado
     * @throws OperacionInvalidaException si no hay stock suficiente, o si el
     *         retorno dejaria mas unidades de las que existen
     */
    @Transactional
    public Movimiento registrar(Long productoId,
                                 TipoMovimiento tipo,
                                 int cantidad,
                                 UsuarioPrincipal usuario) {

        if (cantidad == 0) {
            throw new OperacionInvalidaException(
                    "La cantidad debe ser distinta de cero");
        }

        // Bloqueo pesimista: nadie mas puede tocar este producto hasta que
        // termine esta transaccion.
        Producto producto = productoRepository.findByIdParaActualizar(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El producto " + productoId + " no existe"));

        int disponible = producto.getCantidadDisponible();
        // Positivo saca material, negativo lo devuelve: por eso se resta la
        // cantidad. VENTA de 3 sobre 10 disponibles deja 7.
        int nuevoDisponible = disponible - cantidad;

        // La validacion va sobre el valor recien leido (con el bloqueo puesto),
        // por eso el bloqueo es obligatorio y no una simple optimizacion.
        if (nuevoDisponible < 0) {
            throw new OperacionInvalidaException(
                    ("No hay stock suficiente de %s: se piden %d unidades y solo "
                            + "hay %d disponibles en bodega.")
                            .formatted(producto.getNombre(),
                                    Math.abs(cantidad),
                                    disponible));
        }

        if (nuevoDisponible > producto.getCantidadTotal()) {
            throw new OperacionInvalidaException(
                    ("El retorno supera el stock fisico de %s: el producto tiene %d "
                            + "unidades en total y se intentarian dejar %d. "
                            + "Revisa las cantidades registradas en el retorno.")
                            .formatted(producto.getNombre(),
                                    producto.getCantidadTotal(),
                                    nuevoDisponible));
        }

        producto.setCantidadDisponible(nuevoDisponible);
        producto.setEstado(estadoSegunStock(producto, nuevoDisponible));
        productoRepository.save(producto);

        Usuario ejecutor = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + usuario.getId() + " no existe"));

        return movimientoRepository.save(Movimiento.builder()
                .tipo(tipo)
                .producto(producto)
                .cantidad(cantidad)
                .usuario(ejecutor)
                .build());
    }

    /**
     * El estado refleja si hay algo fuera de bodega.
     *
     * <p>Un producto puede tener parte rentada y parte en bodega. En ese
     * caso se marca ARRENDADO, porque es lo que el operador necesita ver de
     * un vistazo: hay material que no esta en la bodega.
     */
    private EstadoProducto estadoSegunStock(Producto producto, int disponible) {
        return disponible >= producto.getCantidadTotal()
                ? EstadoProducto.EN_BODEGA
                : EstadoProducto.ARRENDADO;
    }
}

