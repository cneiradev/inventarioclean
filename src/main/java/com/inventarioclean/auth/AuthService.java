package com.inventarioclean.auth;

import com.inventarioclean.auth.dto.LoginRequest;
import com.inventarioclean.auth.dto.LoginResponse;
import com.inventarioclean.common.exception.CredencialesInvalidasException;
import com.inventarioclean.common.security.JwtService;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticacion: valida credenciales y emite el token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // El username se define en mayusculas (FPEREZ), asi que se normaliza
        // la entrada: escribir "fprez" tambien debe funcionar.
        String username = request.getUsername().trim().toUpperCase();

        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElse(null);

        // Se verifica el hash incluso si el usuario no existe, con un hash
        // ficticio, para que el tiempo de respuesta no delate que cuentas hay.
        if (usuario == null) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new CredencialesInvalidasException();
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException();
        }

        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        String token = jwtService.generateToken(principal);

        log.info("Login exitoso: {} ({})", usuario.getUsername(), usuario.getRol());

        return LoginResponse.builder()
                .token(token)
                .tipoToken("Bearer")
                .usuarioId(usuario.getId())
                .username(usuario.getUsername())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .nombreCompleto(usuario.getNombreCompleto())
                .iniciales(usuario.getIniciales())
                .rol(usuario.getRol())
                .build();
    }

    /**
     * Hash BCrypt valido y fijo, usado solo para igualar el tiempo de
     * respuesta cuando el usuario no existe. No corresponde a ninguna
     * contrasena real.
     */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEeO8Kx1UwZ4Y0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z0Z";
}

