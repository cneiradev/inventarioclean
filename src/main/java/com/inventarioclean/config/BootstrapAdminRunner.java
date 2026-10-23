package com.inventarioclean.config;

import com.inventarioclean.usuario.Rol;
import com.inventarioclean.usuario.Usuario;
import com.inventarioclean.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el usuario ADMINISTRADOR inicial.
 *
 * <p>Hace falta porque el sistema arranca sin usuarios y sin ellos nadie
 * podria entrar a crear los demas. Se ejecuta solo si la tabla esta vacia,
 * de modo que es idempotente.
 *
 * <p>La contrasena se toma de BOOTSTRAP_ADMIN_PASSWORD. Si no se define en
 * produccion, NO se crea nada y se avisa por log: crear una contrasena por
 * defecto seria una puerta abierta. En desarrollo el perfil local define una.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-username:ADMIN}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return;     // ya hay usuarios: nada que hacer
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("""

                    =====================================================================
                    No hay usuarios en la base y BOOTSTRAP_ADMIN_PASSWORD no esta
                    definido, asi que NO se creo ningun administrador.
                    El sistema quedara inaccesible hasta que se cree un usuario
                    administrador por otro medio.
                    =====================================================================
                    """);
            return;
        }

        String username = adminUsername.trim().toUpperCase();
        boolean esDev = activeProfile.contains("local");

        if (esDev) {
            log.warn("""

                    =====================================================================
                    ADMINISTRADOR DE DESARROLLO CREADO
                      usuario:    {}
                      contrasena: {}
                    Esto es solo para desarrollo. En produccion define
                    BOOTSTRAP_ADMIN_PASSWORD antes del primer arranque.
                    =====================================================================
                    """, username, adminPassword);
        } else {
            log.info("Administrador inicial creado: {}", username);
        }

        usuarioRepository.save(Usuario.builder()
                .nombre("Administrador")
                .apellido("Sistema")
                .email("admin@inventarioar.local")
                .username(username)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .rol(Rol.ADMINISTRADOR)
                .activo(true)
                .build());
    }
}

