package com.inventarioclean.auditoria;

import com.inventarioclean.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Evidencia de una accion. Tabla append-only y de solo lectura.
 *
 * <p>El cliente pidio que toda accion de ADMINISTRADOR y OPERADOR quede
 * registrada con quien la hizo y cuando.
 *
 * <p>{@link #username} se desnormaliza a proposito: si el usuario se elimina
 * o cambia de nombre, la evidencia historica debe seguir siendo legible.
 * {@link #usuarioId} queda en NULL si el usuario se borra (ON DELETE SET NULL).
 */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Copia del username en el momento de la accion. No se desvincula. */
    @Column(nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccionAuditoria accion;

    /** Tipo de entidad afectada: 'usuario', 'producto', 'renta'. */
    @Column(nullable = false, length = 50)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    /** Detalle libre: valores antes/despues, codigos, contexto. */
    @Column(length = 4000)
    private String detalle;

    @Column(length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

