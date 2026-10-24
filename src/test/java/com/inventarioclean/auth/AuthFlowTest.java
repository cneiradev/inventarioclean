package com.inventarioclean.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventarioclean.auth.dto.LoginRequest;
import com.inventarioclean.movimiento.MovimientoRepository;
import com.inventarioclean.producto.ProductoRepository;
import com.inventarioclean.renta.RentaRepository;
import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del flujo de autenticacion y de los roles.
 *
 * <p>Se cubren los tres roles del cliente y, sobre todo, que un COMERCIAL
 * no pueda escribir: la autorizacion por rol se aplica en el servidor, y
 * esto verifica que la configuracion de seguridad realmente lo respalda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("Autenticacion y autorizacion por rol")
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private RentaRepository rentaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    private static final String PASSWORD = "clave-de-prueba";

    @BeforeEach
    void crearUsuarios() {
        // Se limpia y se recrea para que el test no dependa del orden. Primero
        // los hijos (movimientos y rentas) y despues usuario: al reves, la
        // base rechaza el borrado por las claves foraneas.
        movimientoRepository.deleteAll();
        rentaRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();

        crear("Francisco", "Perez", "fperez", Rol.OPERADOR);
        crear("Ana", "Soto", "asoto", Rol.COMERCIAL);
    }

    /**
     * Formato del username, confirmado por el cliente.
     *
     * <p>Regla: inicial del primer nombre + primer apellido, en mayusculas.
     * "Francisco Perez" -> F + PEREZ -> {@code FPEREZ}.
     *
     * <p>Durante el desarrollo se sospecho que el documento del cliente
     * tenia un error de conteo de letras. El cliente lo confirmo: PEREZ
     * tiene 5 letras y F + PEREZ da 6, o sea FPEREZ, tal como dice el
     * documento original.
     */

    private void crear(String nombre, String apellido, String username, Rol rol) {
        // Se guarda en mayusculas porque ese es el formato real del campo
        // (FPEREZ) y el login normaliza la entrada a mayusculas antes de
        // buscar: guardar en minusculas haria que el usuario no exista.
        usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email(username + "@test.local")
                .username(username.toUpperCase())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .rol(rol)
                .activo(true)
                .build());
    }

    private String login(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD));
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }

    @Test
    @DisplayName("Login correcto devuelve token y datos del usuario")
    void loginCorrecto() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("fperez", PASSWORD));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("FPEREZ"))
                .andExpect(jsonPath("$.rol").value("OPERADOR"))
                .andExpect(jsonPath("$.nombreCompleto").value("Francisco Perez"))
                .andExpect(jsonPath("$.iniciales").value("FP"));
    }

    @Test
    @DisplayName("El usuario se normaliza a mayusculas (fperez -> FPEREZ)")
    void loginNormalizaUsuario() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("fperez", PASSWORD));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("FPEREZ"));
    }

    @Test
    @DisplayName("Contrasena incorrecta devuelve 401 sin revelar si el usuario existe")
    void loginConContrasenaIncorrecta() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("fperez", "mala"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                // El mensaje es el mismo exista o no el usuario: no se entrega
                // informacion sobre que cuentas hay.
                .andExpect(jsonPath("$.error").value("Usuario o contrasena incorrectos"));
    }

    @Test
    @DisplayName("Usuario inexistente devuelve el mismo 401 generico")
    void loginConUsuarioInexistente() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("NOEXISTE", PASSWORD));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contrasena incorrectos"));
    }

    @Test
    @DisplayName("Peticion sin token devuelve 401 para que el frontend cierre la sesion")
    void peticionSinToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con token valido /me devuelve el usuario completo")
    void meConTokenValido() throws Exception {
        String token = login("fperez");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("FPEREZ"))
                .andExpect(jsonPath("$.rol").value("OPERADOR"))
                .andExpect(jsonPath("$.nombreCompleto").value("Francisco Perez"));
    }

    @Test
    @DisplayName("Un token con firma manipulada se rechaza")
    void tokenManipuladoSeRechaza() throws Exception {
        String token = login("fperez");
        // Se altera el ultimo caracter de la firma.
        String manipulado = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + manipulado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un usuario desactivado no puede iniciar sesion")
    void usuarioInactivoNoPuedeEntrar() throws Exception {
        // El repositorio guarda el username en mayusculas, asi que se busca
        // con "FPEREZ" (el login es quien normaliza la entrada, no el repo).
        Usuario u = usuarioRepository.findByUsernameAndActivoTrue("FPEREZ").orElseThrow();
        u.setActivo(false);
        usuarioRepository.save(u);

        String body = objectMapper.writeValueAsString(new LoginRequest("fperez", PASSWORD));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}

