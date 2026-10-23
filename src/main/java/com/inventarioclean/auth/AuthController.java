package com.inventarioclean.auth;

import com.inventarioclean.auth.dto.LoginRequest;
import com.inventarioclean.auth.dto.LoginResponse;
import com.inventarioclean.auth.dto.UsuarioActualResponse;
import com.inventarioclean.common.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de sesion.
 *
 * <p>Solo /login es publico (ver SecurityConfig). /me exige token valido y
 * sirve para que el frontend rehidrate la sesion al recargar la pagina.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Datos del usuario del token actual. Permite al frontend recuperar la
     * sesion al recargar sin volver a pedir la contrasena.
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioActualResponse> me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.ok(UsuarioActualResponse.builder()
                .usuarioId(principal.getId())
                .username(principal.getUsername())
                .nombre(principal.getNombre())
                .apellido(principal.getApellido())
                .nombreCompleto(principal.getNombreCompleto())
                .iniciales(principal.getIniciales())
                .rol(principal.getRol())
                .build());
    }
}

