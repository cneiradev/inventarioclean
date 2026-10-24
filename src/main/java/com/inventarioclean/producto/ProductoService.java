package com.inventarioclean.producto;

import com.inventarioclean.auditoria.AccionAuditoria;
import com.inventarioclean.auditoria.AuditoriaService;
import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.exception.RecursoNoEncontradoException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.producto.dto.ProductoRequest;
import com.inventarioclean.producto.dto.ProductoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gestion de productos de venta y renta.
 *
 * <p><b>Regla central del modulo:</b> {@code cantidad_disponible} NO se
 * edita desde aqui salvo al crear el producto. A partir de ese momento la
 * manda la Fase 3, que la actualiza con movimientos. Esta es la diferencia
 * clave frente al control manual actual: el stock no se "corrige" escribiendo
 * un numero, se corrige registrando por que se movio.
 */
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final AuditoriaService auditoriaService;

    @Transactional
    public ProductoResponse crear(ProductoRequest request, UsuarioPrincipal auditor) {
        String sku = request.getSku().trim().toUpperCase();

        if (productoRepository.existsBySku(sku)) {
            throw new OperacionInvalidaException(
                    "Ya existe un producto con el SKU " + sku);
        }

        int stockMinimo = request.getStockMinimo() == null ? 0 : request.getStockMinimo();

        Producto producto = Producto.builder()
                .sku(sku)
                .nombre(request.getNombre().trim())
                .medidas(trimToNull(request.getMedidas()))
                .cantidadTotal(request.getCantidadTotal())
                // Al crear, todo el stock esta disponible: todavia no se ha
                // rented ni vendido nada.
                .cantidadDisponible(request.getCantidadTotal())
                .precio(normalizarPrecio(request.getPrecio()))
                .tipoComercio(request.getTipoComercio())
                .estado(request.getEstado() == null
                        ? EstadoProducto.EN_BODEGA
                        : request.getEstado())
                .stockMinimo(stockMinimo)
                .activo(true)
                .build();

        Producto guardado = productoRepository.save(producto);

        auditoriaService.registrar(auditor, AccionAuditoria.CREAR_PRODUCTO,
                "producto", guardado.getId(),
                "Se creo el producto %s (%s, %s) con %d unidades a %s"
                        .formatted(guardado.getSku(), guardado.getNombre(),
                                guardado.getTipoComercio(),
                                guardado.getCantidadTotal(),
                                guardado.getPrecio()));

        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Page<ProductoResponse> listar(TipoComercio tipo, String texto,
                                         boolean soloBajo, Pageable pageable) {
        String busqueda = StringUtils.hasText(texto) ? texto.trim() : null;
        return productoRepository.buscar(tipo, busqueda, soloBajo, pageable)
                .map(ProductoService::toResponse);
    }

    /**
     * Busca un producto por SKU. El documento pide que tanto para modificar
     * como para eliminar se busque "por SKU/CodigoProducto".
     */
    @Transactional(readOnly = true)
    public ProductoResponse buscarPorSku(String sku) {
        String normalizado = sku.trim().toUpperCase();
        return productoRepository.findBySkuAndActivoTrue(normalizado)
                .map(ProductoService::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningun producto con el SKU " + normalizado));
    }

    /** Busqueda para el formulario del operador: por SKU o por nombre. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> buscarPorCodigoONombre(String codigo) {
        if (!StringUtils.hasText(codigo)) {
            return List.of();
        }
        return productoRepository.buscarPorCodigoONombre(codigo.trim())
                .stream()
                .map(ProductoService::toResponse)
                .toList();
    }

    /** Productos en stock bajo, para el dashboard. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> stockBajo(TipoComercio tipo) {
        return productoRepository.stockBajo(tipo).stream()
                .map(ProductoService::toResponse)
                .toList();
    }

@Transactional
    public ProductoResponse actualizar(String sku, ProductoRequest request,
                                        UsuarioPrincipal auditor) {
        String normalizado = sku.trim().toUpperCase();
        Producto producto = productoRepository.findBySkuAndActivoTrue(normalizado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningun producto con el SKU " + normalizado));

        // Cambiar el SKU es peligroso: habria que reescribir todos los
        // movimientos historicos que lo referencian. Se mantiene fijo.
        String nuevoSku = request.getSku().trim().toUpperCase();
        if (!nuevoSku.equals(producto.getSku())) {
            throw new OperacionInvalidaException(
                    "No se puede cambiar el SKU de un producto existente ("
                            + producto.getSku() + " -> " + nuevoSku
                            + "). Elimina el producto y crealo de nuevo.");
        }

        StringBuilder cambios = new StringBuilder();

        if (!request.getNombre().trim().equals(producto.getNombre())) {
            cambios.append(String.format("nombre: '%s' -> '%s'; ",
                    producto.getNombre(), request.getNombre().trim()));
            producto.setNombre(request.getNombre().trim());
        }

        String medidas = trimToNull(request.getMedidas());
        if (medidas == null ? producto.getMedidas() != null
                : !medidas.equals(producto.getMedidas())) {
            cambios.append(String.format("medidas: '%s' -> '%s'; ",
                    producto.getMedidas(), medidas));
            producto.setMedidas(medidas);
        }

        // El total SI se puede editar: es una correccion de inventario.
        // El disponible NO, porque depende de los movimientos.
        if (!request.getCantidadTotal().equals(producto.getCantidadTotal())) {
            int reservado = producto.getCantidadTotal() - producto.getCantidadDisponible();

            if (request.getCantidadTotal() < reservado) {
                throw new OperacionInvalidaException(
                        ("No puedes dejar %d unidades en total: hay %d ya rentadas "
                                + "o vendidas. Registra el retorno primero.")
                                .formatted(request.getCantidadTotal(), reservado));
            }

            cambios.append(String.format("cantidad total: %d -> %d; ",
                    producto.getCantidadTotal(), request.getCantidadTotal()));

            producto.setCantidadTotal(request.getCantidadTotal());
            // El disponible acompana al total, manteniendo lo reservado.
            producto.setCantidadDisponible(request.getCantidadTotal() - reservado);
        }

        BigDecimal precio = normalizarPrecio(request.getPrecio());
        if (precio.compareTo(producto.getPrecio()) != 0) {
            cambios.append(String.format("precio: %s -> %s; ",
                    producto.getPrecio(), precio));
            producto.setPrecio(precio);
        }

        if (request.getTipoComercio() != null
                && request.getTipoComercio() != producto.getTipoComercio()) {
            cambios.append(String.format("tipo: %s -> %s; ",
                    producto.getTipoComercio(), request.getTipoComercio()));
            producto.setTipoComercio(request.getTipoComercio());
        }

        if (request.getEstado() != null && request.getEstado() != producto.getEstado()) {
            cambios.append(String.format("estado: %s -> %s; ",
                    producto.getEstado(), request.getEstado()));
            producto.setEstado(request.getEstado());
        }

        Integer minimo = request.getStockMinimo() == null ? 0 : request.getStockMinimo();
        if (!minimo.equals(producto.getStockMinimo())) {
            cambios.append(String.format("stock minimo: %d -> %d; ",
                    producto.getStockMinimo(), minimo));
            producto.setStockMinimo(minimo);
        }

        Producto guardado = productoRepository.save(producto);

        auditoriaService.registrar(auditor, AccionAuditoria.MODIFICAR_PRODUCTO,
                "producto", guardado.getId(),
                "Cambios en %s -> %s"
                        .formatted(guardado.getSku(),
                                cambios.length() == 0
                                        ? "sin cambios"
                                        : cambios.toString().trim()));

        return toResponse(guardado);
    }
/**
     * Desactiva un producto (soft delete).
     *
     * <p>Un producto que ya tuvo movimientos NO se borra fisicamente: el
     * historial de renta y venta debe seguir siendo trazable, que es
     * justamente lo que el cliente pidio para resolver dudas a futuro.
     */
    @Transactional
    public void eliminar(String sku, UsuarioPrincipal auditor) {
        String normalizado = sku.trim().toUpperCase();
        Producto producto = productoRepository.findBySkuAndActivoTrue(normalizado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningun producto con el SKU " + normalizado));

        producto.setActivo(false);
        productoRepository.save(producto);

        auditoriaService.registrar(auditor, AccionAuditoria.ELIMINAR_PRODUCTO,
                "producto", producto.getId(),
                "Se desactivo el producto %s (%s)"
                        .formatted(producto.getSku(), producto.getNombre()));
    }

    /** Redondea a 2 decimales: los precios no manejan mas precision. */
    private BigDecimal normalizarPrecio(BigDecimal precio) {
        return precio.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String trimToNull(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    static ProductoResponse toResponse(Producto p) {
        return new ProductoResponse(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getMedidas(),
                p.getCantidadTotal(),
                p.getCantidadDisponible(),
                p.getPrecio(),
                p.getTipoComercio(),
                p.getEstado(),
                p.getStockMinimo(),
                p.isActivo(),
                p.esStockBajo());
    }
}

