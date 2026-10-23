package com.inventarioclean.usuario.dto;

import com.inventarioclean.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos para modificar un usuario.
 *
 * <p>La contrasena es opcional a proposito: si viene vacia se conserva la
 * actual. Asi el administrador puede corregir un email o un rol sin obligar
 * al usuario a cambiar su clave.
 */
@Getter
@Setter
public class ActualizarUsuarioRequest {

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

    /** Si es null se conserva el rol actual. */
    private Rol rol;

    /** Si viene vacia o null, se conserva la contrasena actual. */
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Pattern(regexp = "^\\S*$", message = "La contrasena no puede contener espacios")
    private String password;
}
