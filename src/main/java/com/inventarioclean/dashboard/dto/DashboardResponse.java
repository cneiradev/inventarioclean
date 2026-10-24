package com.inventarioclean.dashboard.dto;

import java.util.List;

/**
 * Indicadores del panel principal.
 *
 * <p>El documento pide "un dashboard puede servir para mostrarnos informacion
 * especifica e importante, se puede definir en alguna reunion". Estos son los
 * datos que un sistema de bodega necesita a primera hora: cuanto hay, cuanto
 * esta saliendo, y que hay que reponer.
 */
public record DashboardResponse(
        /** Total de productos activos en el catalogo. */
        long totalProductos,
        long productosVenta,
        /** Productos cuyo disponible esta en o bajo el minimo. */
        long stockBajo,
        /** Unidades que deberian estar en bodega ahora mismo. */
        long unidadesDisponibles,
        /** Detalle del stock bajo, para actuar sobre el. */
        List<ItemStockBajo> productosStockBajo,
        /** Ultimas operaciones de bodega, para ver que paso hace rato. */
        List<UltimaOperacion> ultimasOperaciones
) {
    /** Un producto que necesita reposicion. */
    public record ItemStockBajo(
            Long id,
            String sku,
            String nombre,
            String tipoComercio,
            int cantidadDisponible,
            int stockMinimo
    ) {
    }

    /** Una operacion reciente de bodega. */
    public record UltimaOperacion(
            String tipo,
            String referencia,
            String usuario,
            String fecha
    ) {
    }
}
