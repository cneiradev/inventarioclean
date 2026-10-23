package com.inventarioclean.producto;

/**
 * Estado del producto.
 *
 * <p>EN_BODEGA - fisicamente en bodega.
 * <p>ARRENDADO - hay unidades prestadas. Si solo algunas lo estan, el estado
 *                refleja que hay algo fuera; el detalle exacto vive en los
 *                movimientos y en la cabecera {@code renta}.
 */
public enum EstadoProducto {

    EN_BODEGA,
    ARRENDADO
}

