package com.inventarioclean.usuario;

import com.inventarioclean.auditoria.AccionAuditoria;
import com.inventarioclean.auditoria.Auditoria;
import com.inventarioclean.auditoria.AuditoriaRepository;
import com.inventarioclean.movimiento.MovimientoRepository;
import com.inventarioclean.renta.RentaRepository;
import com.inventarioclean.common.exception.OperacionInvalidaException;
import com.inventarioclean.common.exception.RecursoNoEncontradoException;
import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.usuario.dto.ActualizarUsuarioRequest;
import com.inventarioclean.usuario.dto.CrearUsuarioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del CRUD de usuarios.
 *
 * <p>Se verifica lo que pide el documento y las reglas que se decidio
 * anadir: el username se genera solo (nunca se acepta desde el formulario),
 * no se puede desactivar al unico administrador, y toda accion queda
 * auditada.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Gestion de usuarios")
class UsuarioServiceTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private RentaRepository rentaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UsuarioPrincipal auditor;

    @BeforeEach
    void preparar() {
        // El orden importa: primero los hijos (movimientos y rentas) y despues
        // usuario. Al revÃ©s, la base rechaza el borrado por las claves
        // foraneas.
        movimientoRepository.deleteAll();
        rentaRepository.deleteAll();
        usuarioRepository.deleteAll();
        auditoriaRepository.deleteAll();
        usuarioRepository.flush();

        // Un administrador real, para que las acciones queden auditadas.
        Usuario admin = construir("Admin", "Sistema", "ADMIN", Rol.ADMINISTRADOR);
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        usuarioRepository.save(admin);

        auditor = new UsuarioPrincipal(admin);
    }

    private Usuario construir(String nombre, String apellido,
                              String username, Rol rol) {
        return Usuario.builder()
                .nombre(nombre).apellido(apellido)
                .email(username.toLowerCase() + "@test.local")
                .username(username)
                .passwordHash("hash")
                .rol(rol).activo(true)
                .build();
    }

    private CrearUsuarioRequest crearRequest(String nombre, String apellido,
                                             String email, Rol rol) {
        CrearUsuarioRequest r = new CrearUsuarioRequest();
        r.setNombre(nombre);
        r.setApellido(apellido);
        r.setEmail(email);
        r.setRol(rol);
        r.setPassword("clave12345");
        return r;
    }

    @Test
    @DisplayName("Crea un usuario y genera el username con la regla del cliente")
    void creaUsuarioYGeneraUsername() {
        var creado = usuarioService.crear(
                crearRequest("Francisco", "Perez", "fperez@test.local", Rol.OPERADOR),
                auditor);

        assertThat(creado.username()).isEqualTo("FPEREZ");
        assertThat(creado.rol()).isEqualTo(Rol.OPERADOR);
        assertThat(creado.activo()).isTrue();
    }

    @Test
    @DisplayName("La contrasena se guarda hasheada, nunca en texto plano")
    void guardaContrasenaHasheada() {
        usuarioService.crear(
                crearRequest("Francisco", "Perez", "fperez@test.local", Rol.OPERADOR),
                auditor);

        Usuario guardado = usuarioRepository.findByUsername("FPEREZ").orElseThrow();
        assertThat(guardado.getPasswordHash()).isNotEqualTo("clave12345");
        assertThat(guardado.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("clave12345", guardado.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Dos usuarios con el mismo nombre reciben usernames distintos")
    void resuelveColisionesDeUsername() {
        usuarioService.crear(
                crearRequest("Francisco", "Perez", "fperez1@test.local", Rol.OPERADOR),
                auditor);

        var segundo = usuarioService.crear(
                crearRequest("Francisco", "Perez", "fperez2@test.local", Rol.COMERCIAL),
                auditor);

        assertThat(segundo.username()).isEqualTo("FPEREZ1");
    }

    @Test
    @DisplayName("Rechaza un correo ya registrado")
    void rechazaCorreoDuplicado() {
        usuarioService.crear(
                crearRequest("Ana", "Soto", "mismo@test.local", Rol.OPERADOR), auditor);

        assertThatThrownBy(() -> usuarioService.crear(
                crearRequest("Luis", "Gomez", "MISMO@test.local", Rol.OPERADOR), auditor))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("mismo@test.local");
    }

    @Test
    @DisplayName("Busca por username aceptando minusculas")
    void buscaPorUsername() {
        usuarioService.crear(
                crearRequest("Ana", "Soto", "asoto@test.local", Rol.COMERCIAL), auditor);

        assertThat(usuarioService.buscarPorUsername("ASOTO").username()).isEqualTo("ASOTO");
        assertThat(usuarioService.buscarPorUsername("asoto").username()).isEqualTo("ASOTO");
    }

    @Test
    @DisplayName("Error claro al buscar un username que no existe")
    void errorBusquedaInexistente() {
        assertThatThrownBy(() -> usuarioService.buscarPorUsername("NOEXISTE"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("NOEXISTE");
    }
@Test
    @DisplayName("Modificar sin contrasena conserva la clave actual")
    void conservaContrasenaSiNoSeEnvia() {
        var creado = usuarioService.crear(
                crearRequest("Ana", "Soto", "asoto@test.local", Rol.COMERCIAL), auditor);

        ActualizarUsuarioRequest req = new ActualizarUsuarioRequest();
        req.setNombre("Ana Maria");
        req.setApellido("Soto");
        req.setEmail("asoto@test.local");
        req.setRol(Rol.OPERADOR);
        req.setPassword("");        // vacia = mantener

        usuarioService.actualizar(creado.id(), req, auditor);

        Usuario guardado = usuarioRepository.findById(creado.id()).orElseThrow();
        assertThat(passwordEncoder.matches("clave12345", guardado.getPasswordHash()))
                .as("la contrasena no debio cambiar").isTrue();
        assertThat(guardado.getRol()).isEqualTo(Rol.OPERADOR);
        assertThat(guardado.getNombreCompleto()).isEqualTo("Ana Maria Soto");
    }

    @Test
    @DisplayName("No se puede desactivar al unico administrador")
    void protegeAlUltimoAdministrador() {
        // Si el unico admin se desactiva, nadie podria volver a administrar
        // el sistema: quedaria sin acceso para siempre.
        long idAdmin = usuarioRepository.findByUsername("ADMIN").orElseThrow().getId();

        assertThatThrownBy(() -> usuarioService.eliminar(idAdmin, auditor))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("unico administrador");

        assertThat(usuarioRepository.findById(idAdmin).orElseThrow().isActivo())
                .as("el administrador debe seguir activo").isTrue();
    }

    @Test
    @DisplayName("Eliminar es soft delete: la fila sigue existiendo")
    void eliminarEsSoftDelete() {
        var creado = usuarioService.crear(
                crearRequest("Ana", "Soto", "asoto@test.local", Rol.COMERCIAL), auditor);

        usuarioService.eliminar(creado.id(), auditor);

        // La fila permanece para conservar la evidencia historica...
        Usuario enBase = usuarioRepository.findById(creado.id()).orElseThrow();
        assertThat(enBase.isActivo()).as("debe quedar inactivo").isFalse();

        // ...pero el usuario ya no puede iniciar sesion.
        assertThat(usuarioRepository.findByUsernameAndActivoTrue("ASOTO")).isEmpty();
    }

    @Test
    @DisplayName("Cada accion queda auditada con quien la hizo")
    void auditaCadaAccion() {
        var creado = usuarioService.crear(
                crearRequest("Ana", "Soto", "asoto@test.local", Rol.COMERCIAL), auditor);

        usuarioService.eliminar(creado.id(), auditor);

        List<Auditoria> registros = auditoriaRepository.findAll();
        assertThat(registros).hasSize(2);

        Auditoria creacion = registros.stream()
                .filter(a -> a.getAccion() == AccionAuditoria.CREAR_USUARIO)
                .findFirst().orElseThrow();
        assertThat(creacion.getUsername()).isEqualTo("ADMIN");
        assertThat(creacion.getEntidadId()).isEqualTo(creado.id());
        assertThat(creacion.getDetalle()).contains("ASOTO");

        Auditoria baja = registros.stream()
                .filter(a -> a.getAccion() == AccionAuditoria.ELIMINAR_USUARIO)
                .findFirst().orElseThrow();
        assertThat(baja.getDetalle()).contains("desactivo");
    }

    @Test
    @DisplayName("La evidencia conserva el username aunque el usuario se de de baja")
    void evidenciaConservaUsername() {
        // El username se desnormaliza justamente para esto: si el usuario se
        // da de baja, sus acciones siguen siendo atribuibles a alguien.
        var creado = usuarioService.crear(
                crearRequest("Ana", "Soto", "asoto@test.local", Rol.COMERCIAL), auditor);

        usuarioService.eliminar(creado.id(), auditor);

        assertThat(auditoriaRepository.findAll())
                .isNotEmpty()
                .allMatch(a -> "ADMIN".equals(a.getUsername()));
    }
}

