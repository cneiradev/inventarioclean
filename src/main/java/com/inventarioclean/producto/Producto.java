package com.inventarioclean.producto;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Producto de bodega: insumo de venta o de renta.
 *
 * <p><b>Regla central del sistema:</b> {@link #cantidadTotal} es la cantidad
 * fisica en bodega y no se toca al rentar ni al vender.
 * {@link #cantidadDisponible} es lo que se puede operar HOY y NUNCA se edita
 * a mano desde la UI: se deriva de los movimientos. Esa distincion es lo que
 * reemplaza el control manual que hoy genera informacion erronea.
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codigo de producto. Clave de busqueda para admin y operador. */
    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false, length = 200)
    private String nombre;

    /**
     * Texto libre: la reunion del 24/09 dejo "medidas" sin definir.
     * Si el cliente confirma un formato (por ejemplo 2x3x1 m), se migra a
     * columnas o a una tabla producto_medida.
     */
    @Column(length = 200)
    private String medidas;

    /** Cantidad fisica en bodega. */
    @Column(name = "cantidad_total", nullable = false)
    private Integer cantidadTotal;

    /** Cantidad operable hoy. Se actualiza solo por movimientos. */
    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    /** DECIMAL, nunca float: el dinero no es binario. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comercio", nullable = false, length = 10)
    private TipoComercio tipoComercio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoProducto estado = EstadoProducto.EN_BODEGA;

    /** Umbral del dashboard de "stock bajo". Configurable por producto. */
    @Column(name = "stock_minimo", nullable = false)
    @Builder.Default
    private Integer stockMinimo = 0;

    /**
     * Soft delete. Un producto con movimientos historicos NUNCA se borra
     * fisicamente: se desactiva y el historial queda intacto.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Un producto esta en stock bajo cuando su disponible cae por debajo del
     * minimo configurado. Criterio por defecto (a confirmar con el cliente):
     * unidades, no porcentaje.
     */
    public boolean esStockBajo() {
        return cantidadDisponible != null
                && stockMinimo != null
                && cantidadDisponible <= stockMinimo;
    }
}

