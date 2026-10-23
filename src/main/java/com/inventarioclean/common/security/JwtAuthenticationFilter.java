package com.inventarioclean.common.security;

import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro que lee el JWT de la cabecera Authorization y establece la
 * autenticacion del request.
 *
 * <p>Si el token es invalido o expiro, la peticion sigue su curso sin
 * autenticacion y acaba en 401 por la regla authorizeHttpRequests. No se
 * corta el filtro: es el comportamiento estandar para APIs sin estado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, java.io.IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length());

        try {
            Claims claims = jwtService.parseToken(token);
            String username = claims.getSubject();

            // Se relee el usuario desde la base (y no solo del token) para que
            // una eliminacion o un cambio de rol surta efecto de inmediato,
            // sin esperar a que expire el token.
            Usuario usuario = usuarioRepository
                    .findByUsernameAndActivoTrue(username)
                    .orElseThrow(() -> new UsernameNotFoundException(username));

            UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException | IllegalArgumentException e) {
            // Token caducado, malformado o con firma invalida: se loguea a
            // debug y la peticion sigue sin autenticar (-> 401).
            log.debug("Token rechazado: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

