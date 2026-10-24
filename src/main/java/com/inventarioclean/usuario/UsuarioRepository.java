package com.inventarioclean.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Login: solo usuarios activos. */
    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    /** Busqueda en la gestion de usuarios: incluye los desactivados. */
    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

