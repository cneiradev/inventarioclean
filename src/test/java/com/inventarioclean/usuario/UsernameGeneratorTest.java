package com.inventarioclean.usuario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la regla de generacion de usernames.
 *
 * <p>La regla la fijo el cliente: inicial del primer nombre + primer
 * apellido, en mayusculas. Estos tests cubren los casos que el documento no
 * menciona pero que aparecen en la operacion diaria: acentos, espacios,
 * nombres repetidos y nombres muy largos.
 */
@DisplayName("Generador de usernames")
class UsernameGeneratorTest {

    private final UsernameGenerator generator = new UsernameGenerator();

    /** Predicate que nunca encuentra nada: el caso "usuario libre". */
    private static final Predicate<String> NUNCA_EXISTE = s -> false;

    @Test
    @DisplayName("Francisco Perez -> FPEREZ (el ejemplo del documento)")
    void ejemploDelDocumento() {
        assertThat(generator.generar("Francisco", "Perez")).isEqualTo("FPEREZ");
    }

    @Test
    @DisplayName("Se elimina el acento: MuÃ±oz -> MUNOZ, no MUÃ‘OZ")
    void eliminaAcentos() {
        // Sin esto, el usuario tendria que escribir la eÃ±e en cada login.
        assertThat(generator.generar("JosÃ©", "MuÃ±oz")).isEqualTo("JMUNOZ");
        assertThat(generator.generar("Ãngel", "PÃ©rez")).isEqualTo("APEREZ");
    }

    @Test
    @DisplayName("Marta MuÃ±oz -> MMUNOZ: se suma la inicial, no se reemplaza")
    void inicialSeSumaAlApellido() {
        // La inicial y el apellido empiezan con la misma letra, pero el
        // resultado las concatena: M + MUÃ‘OZ -> MMUNOZ.
        assertThat(generator.generar("Marta", "MuÃ±oz")).isEqualTo("MMUNOZ");
    }

    @Test
    @DisplayName("Quita espacios y acentos del apellido compuesto")
    void limpiaCaracteresEspeciales() {
        // "De la Fuente" es un solo apellido: se toma completo, no solo la
        // primera palabra. El nombre con espacios alrededor tambien debe
        // funcionar: se recorta antes de tomar la inicial.
        assertThat(generator.generar(" Juan ", " De la Fuente ")).isEqualTo("JDELAFUENTE");
    }

    @Test
    @DisplayName("Convierte a mayusculas")
    void convierteAMayusculas() {
        assertThat(generator.generar("ana", "soto")).isEqualTo("ASOTO");
    }

    @Test
    @DisplayName("Dos Francisco Perez no colisionan: FPEREZ y FPEREZ1")
    void resuelveColisiones() {
        // El segundo usuario con el mismo nombre y apellido recibe un
        // correlativo, porque el documento no dice que hacer en este caso y
        // sin esto dos personas distintas no podrian coexistir.
        Set<String> existentes = new HashSet<>();
        existentes.add("FPEREZ");

        String segundo = generator.generarUnico("Francisco", "Perez", existentes::contains);
        assertThat(segundo).isEqualTo("FPEREZ1");
    }

    @Test
    @DisplayName("El correlativo sigue incrementando hasta encontrar un hueco")
    void correlativoSaltaHuecos() {
        Set<String> existentes = Set.of("FPEREZ", "FPEREZ1", "FPEREZ2");

        String siguiente = generator.generarUnico("Francisco", "Perez", existentes::contains);
        assertThat(siguiente).isEqualTo("FPEREZ3");
    }

    @Test
    @DisplayName("Sin colision devuelve el username base tal cual")
    void sinColisionDevuelveBase() {
        String resultado = generator.generarUnico("Francisco", "Perez", NUNCA_EXISTE);
        assertThat(resultado).isEqualTo("FPEREZ");
    }

    @Test
    @DisplayName("Recorta un apellido muy largo para no exceder la columna")
    void recortaNombresLargos() {
        String apellidoMuyLargo = "DE".repeat(30);   // 60 caracteres
        String username = generator.generar("Ana", apellidoMuyLargo);

        assertThat(username).hasSizeLessThanOrEqualTo(50);
        assertThat(username).startsWith("ADE");
    }

    @Test
    @DisplayName("El correlativo tambien cabe en la columna tras recortar")
    void correlativoCabeEnColumna() {
        Set<String> existentes = new HashSet<>();
        String apellidoMuyLargo = "DE".repeat(30);
        String base = generator.generar("Ana", apellidoMuyLargo);
        existentes.add(base);

        String siguiente = generator.generarUnico("Ana", apellidoMuyLargo, existentes::contains);

        assertThat(siguiente).hasSizeLessThanOrEqualTo(50);
        assertThat(siguiente).endsWith("1");
    }

    @Test
    @DisplayName("Rechaza nombre o apellido vacio")
    void rechazaDatosIncompletos() {
        assertThatThrownBy(() -> generator.generar("", "Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");

        assertThatThrownBy(() -> generator.generar("Francisco", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apellido");
    }
}
