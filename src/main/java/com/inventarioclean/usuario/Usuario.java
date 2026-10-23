package com.inventarioclean.usuario;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Usuario del sistema.
 *
 * <p>El {@code username} sigue el formato definido por el cliente:
 * inicial del primer nombre + primer apellido, en mayusculas.
 * Ej: Francisco Perez -&gt; {@code FPEREZ}.
 * Es unico y se normaliza siempre en el backend.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(nullable = false, length = 50, unique = true)
    private String username;

    /**
     * Hash BCrypt. Nunca se expone fuera del backend: la entidad no se
     * serializa directamente a JSON, se usan DTOs.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Nombre completo para mostrar en pantalla y en la auditoria. */
    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    /** Iniciales para el avatar del frontend: Francisco Perez -&gt; FP. */
    public String getIniciales() {
        String n = nombre == null || nombre.isBlank() ? "" : nombre.substring(0, 1);
        String a = apellido == null || apellido.isBlank() ? "" : apellido.substring(0, 1);
        return (n + a).toUpperCase();
    }
}

