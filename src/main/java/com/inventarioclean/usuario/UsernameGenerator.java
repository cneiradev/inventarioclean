package com.inventarioclean.usuario;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Generador de usernames segun la regla del cliente:
 * <b>inicial del primer nombre + primer apellido, en mayusculas.</b>
 *
 * <p>Ejemplos: "Francisco Perez" -&gt; FPEREZ; "Ana Soto" -&gt; ASOTO.
 *
 * <p>Reglas adicionales que el documento no especifica pero hacen falta en
 * la practica:
 * <ul>
 *   <li>Los acentos se eliminan: "MuÃ±oz" -&gt; MUNOZ, no MUÃ‘OZ. Un usuario
 *       tendria que escribir la eÃ±e en cada login.</li>
 *   <li>Si el username ya existe, se agrega un correlativo: FPEREZ1, FPEREZ2.
 *       Sin esto, dos "Francisco Perez" distintos no podrian coexistir.</li>
 * </ul>
 */
@Component
public class UsernameGenerator {

    /** Separador interno antes de limpiar acentos y espacios. */
    private static final int MAX_LONGITUD = 50;

    /**
     * Genera el username base a partir de nombre y apellido.
     *
     * @param nombre   primer nombre del usuario
     * @param apellido primer apellido; los apellidos compuestos ("De la Fuente")
     *                se usan tal cual los entregue el formulario
     * @return username en mayusculas y sin acentos
     */
    public String generar(String nombre, String apellido) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (apellido == null || apellido.isBlank()) {
            throw new IllegalArgumentException("El apellido es obligatorio");
        }

        // Se recorta ANTES de tomar la inicial: con " Juan " el primer
        // caracter seria un espacio, la inicial quedaria vacia y el usuario
        // se crearia como "Perez" en vez de "JPEREZ".
        String nombreRecortado = nombre.trim();
        if (nombreRecortado.isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        String inicial = normalizar(nombreRecortado.substring(0, 1));
        String primerApellido = normalizar(apellido.trim());

        String username = (inicial + primerApellido).toUpperCase();

        if (username.length() > MAX_LONGITUD) {
            username = username.substring(0, MAX_LONGITUD);
        }

        return username;
    }

    /**
     * Genera un username unico, agregando un correlativo si ya existe.
     *
     * @param existe   callback que indica si un username ya existe
     */
    public String generarUnico(String nombre, String apellido,
                               java.util.function.Predicate<String> existe) {
        String base = generar(nombre, apellido);
        if (!existe.test(base)) {
            return base;
        }

        // FPERZ -> FPERZ1 -> FPERZ2 ... hasta encontrar un hueco libre.
        for (int i = 1; i <= 999; i++) {
            String candidato = truncarParaSufijo(base) + i;
            if (!existe.test(candidato)) {
                return candidato;
            }
        }

        throw new IllegalStateException(
                "No se encontro un username disponible para " + nombre + " " + apellido);
    }

    /** Recorta el base para que el correlativo no exceda el limite de la columna. */
    private String truncarParaSufijo(String base) {
        return base.length() > MAX_LONGITUD - 3
                ? base.substring(0, MAX_LONGITUD - 3)
                : base;
    }

    /**
     * Normaliza un fragmento de texto: sin acentos, sin caracteres
     * especiales, solo letras y digitos.
     *
     * <p>Normalizer con NFD separa la letra de su acento ("n" + tilde), y al
     * descartar lo que no es letra queda la "n" sola.
     */
    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD);

        StringBuilder resultado = new StringBuilder();
        for (char c : sinAcentos.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                resultado.append(c);
            }
        }
        return resultado.toString();
    }
}
