package com.inventarioclean.producto.dto;

import com.inventarioclean.producto.EstadoProducto;
import com.inventarioclean.producto.TipoComercio;

import java.math.BigDecimal;

/**
 * Vista de un producto para las tablas de inventario.
 *
 * <p>Incluye {@code stockBajo}, ya calculado, para que el frontend no tenga
 * que replicar el criterio y las dos copias no se desincronicen.
 */
public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String medidas,
        Integer cantidadTotal,
        Integer cantidadDisponible,
        BigDecimal precio,
        TipoComercio tipoComercio,
        EstadoProducto estado,
        Integer stockMinimo,
        boolean activo,
        boolean stockBajo
) {
}
