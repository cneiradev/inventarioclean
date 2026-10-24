package com.inventarioclean.movimiento;

/**
 * Tipo de movimiento de stock.
 *
 * <p>El signo de la cantidad codifica la direccion (positivo = sale de
 * bodega, negativo = vuelve). Ver el comentario de la tabla {@code movimiento}.
 */
public enum TipoMovimiento {

    /** Sale material de bodega por venta (no vuelve). */
    VENTA,
    /** Ingreso manual de material a bodega. */
    ENTRADA_MANUAL,
    /** Correccion de inventario. Puede ser en cualquier direccion. */
    AJUSTE
}

