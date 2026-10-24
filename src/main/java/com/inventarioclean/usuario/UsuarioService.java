package com.inventarioclean.usuario;

import com.inventarioclean.auditoria.AccionAuditoria;
import com.inventarioclean.auditoria.AuditoriaService;
import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.exception.RecursoNoEncontradoException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.usuario.dto.ActualizarUsuarioRequest;
import com.inventarioclean.usuario.dto.CrearUsuarioRequest;
import com.inventarioclean.usuario.dto.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Gestion de usuarios. Solo accesible por ADMINISTRADOR.
 *
 * <p>El username lo genera {@link UsernameGenerator} con la regla del
 * cliente; nunca se recibe desde el formulario.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final AuditoriaService auditoriaService;

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request, UsuarioPrincipal auditor) {
        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(email)) {
            throw new OperacionInvalidaException(
                    "Ya existe un usuario registrado con el correo " + email);
        }

        // Se genera un username unico: si FPERZ ya existe, sale FPERZ1.
        String username = usernameGenerator.generarUnico(
                request.getNombre(), request.getApellido(),
                usuarioRepository::existsByUsername);

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre().trim())
                .apellido(request.getApellido().trim())
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(request.getRol())
                .activo(true)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

        auditoriaService.registrar(auditor, AccionAuditoria.CREAR_USUARIO,
                "usuario", guardado.getId(),
                "Se creo el usuario %s (%s) con rol %s"
                        .formatted(guardado.getUsername(),
                                guardado.getNombreCompleto(),
                                guardado.getRol()));

        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll(Sort.by("nombre", "apellido"))
                .stream()
                .map(UsuarioService::toResponse)
                .toList();
    }

    /**
     * Busca un usuario por su username (el documento pide buscar por nombre
     * de usuario, no por id).
     *
     * <p>Se acepta en cualquier caso: escribir "fperz" y "FPEREZ" dan lo
     * mismo, porque el login tambien normaliza a mayusculas.
     */
    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorUsername(String username) {
        String normalizado = username.trim().toUpperCase();

        return usuarioRepository.findByUsername(normalizado)
                .map(UsuarioService::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningun usuario con el nombre de usuario " + normalizado));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id,
                                      ActualizarUsuarioRequest request,
                                      UsuarioPrincipal auditor) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + id + " no existe"));

        // Evitar que dos usuarios compartan el mismo correo.
        String email = request.getEmail().trim().toLowerCase();
        usuarioRepository.findByEmail(email)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new OperacionInvalidaException(
                            "El correo " + email + " ya pertenece al usuario "
                                    + otro.getUsername());
                });

        StringBuilder cambios = new StringBuilder();

        String nombreAntes = usuario.getNombreCompleto();
        usuario.setNombre(request.getNombre().trim());
        usuario.setApellido(request.getApellido().trim());
        usuario.setEmail(email);

        if (!nombreAntes.equals(usuario.getNombreCompleto())) {
            cambios.append(String.format("nombre: '%s' -> '%s'; ",
                    nombreAntes, usuario.getNombreCompleto()));
        }

        if (request.getRol() != null && request.getRol() != usuario.getRol()) {
            cambios.append(String.format("rol: %s -> %s; ",
                    usuario.getRol(), request.getRol()));
            usuario.setRol(request.getRol());
        }

        // Contrasena vacia = se conserva. Permite editar email o rol sin
        // obligar al usuario a cambiar su clave.
        if (StringUtils.hasText(request.getPassword())) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            cambios.append("contrasena: restablecida; ");
        }

        Usuario guardado = usuarioRepository.save(usuario);

        auditoriaService.registrar(auditor, AccionAuditoria.MODIFICAR_USUARIO,
                "usuario", guardado.getId(),
                "Cambios en %s -> %s"
                        .formatted(guardado.getUsername(), cambios.toString().trim()));

        return toResponse(guardado);
    }

    /**
     * Desactiva un usuario.
     *
     * <p><b>Soft delete.</b> No se borra fisicamente porque la tabla de
     * auditoria guarda evidencia de sus acciones y el documento pide poder
     * resolver dudas a futuro: si la fila desaparece, la evidencia pierde el
     * sentido. Se marca activo = FALSE y el usuario ya no puede entrar.
     *
     * @throws OperacionInvalidaException si se intenta desactivar al ultimo
     *         administrador: el sistema quedaria sin nadie que lo administre.
     */
    @Transactional
    public void eliminar(Long id, UsuarioPrincipal auditor) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + id + " no existe"));

        if (usuario.getRol() == Rol.ADMINISTRADOR) {
            long adminsActivos = usuarioRepository.findAll().stream()
                    .filter(u -> u.getRol() == Rol.ADMINISTRADOR && u.isActivo())
                    .count();

            if (adminsActivos <= 1) {
                throw new OperacionInvalidaException(
                        "No se puede desactivar al unico administrador activo. "
                                + "Crea o habilita otro administrador primero.");
            }
        }

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(auditor, AccionAuditoria.ELIMINAR_USUARIO,
                "usuario", usuario.getId(),
                "Se desactivo el usuario %s (%s, rol %s)"
                        .formatted(usuario.getUsername(),
                                usuario.getNombreCompleto(),
                                usuario.getRol()));
    }

    static UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getUsername(),
                u.getNombre(),
                u.getApellido(),
                u.getNombreCompleto(),
                u.getIniciales(),
                u.getEmail(),
                u.getRol(),
                u.isActivo());
    }
}

