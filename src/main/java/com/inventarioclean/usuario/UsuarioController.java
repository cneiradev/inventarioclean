package com.inventarioclean.usuario;

import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.usuario.dto.ActualizarUsuarioRequest;
import com.inventarioclean.usuario.dto.CrearUsuarioRequest;
import com.inventarioclean.usuario.dto.UsuarioResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de gestion de usuarios.
 *
 * <p>Todos exigen rol ADMINISTRADOR. El {@code @PreAuthorize} es la
 * seguridad real: el menu del frontend lo oculta, pero la API lo rechaza
 * igual aunque alguien llame al endpoint directamente.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    /** Busqueda por nombre de usuario, como pide el documento. */
    @GetMapping("/buscar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioResponse> buscar(@RequestParam String username) {
        return ResponseEntity.ok(usuarioService.buscarPorUsername(username));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CrearUsuarioRequest request,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        UsuarioResponse creado = usuarioService.crear(request, auditor);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        return ResponseEntity.ok(usuarioService.actualizar(id, request, auditor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        usuarioService.eliminar(id, auditor);
        // 204: la operacion se completo y no hay cuerpo que devolver.
        return ResponseEntity.noContent().build();
    }
}
