package com.inventarioclean.usuario.dto;

import com.inventarioclean.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos para crear un usuario.
 *
 * <p>El username NO se pide: lo genera el backend con la regla del cliente
 * (inicial + primer apellido). Pedirlo en el formulario abriria la puerta a
 * que alguien escriba cualquier cosa y rompa la convencion; ademas es
 * justamente lo que el documento pide derivar del nombre.
 */
@Getter
@Setter
public class CrearUsuarioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String apellido;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
    private String email;

    @NotNull(message = "Debes indicar el rol")
    private Rol rol;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    // Limita los caracteres a los que acepta BCrypt. Si se deja libre, un
    // emoji o un caracter de otro alfabeto hacen fallar el hash en silencio.
    @Pattern(regexp = "^\\S+$", message = "La contrasena no puede contener espacios")
    private String password;
}
