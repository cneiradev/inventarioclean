package com.inventarioclean.movimiento;

import com.inventarioclean.producto.Producto;
import com.inventarioclean.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Movimiento de stock. Tabla append-only: es la fuente de verdad.
 *
 * <p>Nunca se actualiza ni se borra una fila. Si un registro quedo mal, se
 * corrige con un movimiento de AJUSTE, de modo que el historico nunca miente.
 *
 * <p><b>El signo de {@link #cantidad} codifica la direccion del stock:</b>
 * <ul>
 *   <li>positivo: sale de bodega (VENTA, AJUSTE a la baja)</li>
 *   <li>negativo: vuelve a bodega (ENTRADA_MANUAL)</li>
 * </ul>
 */
@Entity
@Table(name = "movimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Con signo. Ver la nota de direccion en el javadoc de la clase. */
    @Column(nullable = false)
    private Integer cantidad;

    /** Quien ejecuto el movimiento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** true si el movimiento saca material de bodega. */
    public boolean esSalida() {
        return cantidad != null && cantidad > 0;
    }
}

