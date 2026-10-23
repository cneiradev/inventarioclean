package com.inventarioclean.common.security;

import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Representacion del usuario autenticado dentro de Spring Security.
 *
 * <p>Se construye desde la entidad {@link Usuario} al autenticar. Lleva el id
 * y el username para que los servicios puedan escribir la auditoria sin
 * volver a consultar la base.
 */
@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String nombre;
    private final String apellido;
    private final String nombreCompleto;
    private final String iniciales;
    private final Rol rol;
    private final String passwordHash;

    public UsuarioPrincipal(Usuario usuario) {
        this.id = usuario.getId();
        this.username = usuario.getUsername();
        this.nombre = usuario.getNombre();
        this.apellido = usuario.getApellido();
        this.nombreCompleto = usuario.getNombreCompleto();
        this.iniciales = usuario.getIniciales();
        this.rol = usuario.getRol();
        this.passwordHash = usuario.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security espera los roles con prefijo "ROLE_".
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

