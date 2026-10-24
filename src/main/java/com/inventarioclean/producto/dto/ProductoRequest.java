package com.inventarioclean.producto.dto;

import com.inventarioclean.producto.EstadoProducto;
import com.inventarioclean.producto.TipoComercio;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Datos para crear o modificar un producto.
 *
 * <p>Los campos siguen el formulario del documento: SKU/CodigoProducto,
 * nombre, cantidad, medidas, precio/valor, tipo de comercio (renta o venta)
 * y estado ("En bodega", "Arrendado").
 */
@Getter
@Setter
public class ProductoRequest {

    @NotBlank(message = "El SKU es obligatorio")
    @Size(max = 50, message = "El SKU no puede superar los 50 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "El SKU solo puede tener letras, numeros, punto, guion y guion bajo")
    private String sku;

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 200, message = "El nombre no puede superar los 200 caracteres")
    private String nombre;

    @Size(max = 200, message = "Las medidas no pueden superar los 200 caracteres")
    private String medidas;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 0, message = "La cantidad no puede ser negativa")
    private Integer cantidadTotal;

    /**
     * Stock minimo para el dashboard de "stock bajo". Por defecto queda en 0
     * hasta que el cliente confirme el criterio.
     */
    @Min(value = 0, message = "El stock minimo no puede ser negativo")
    private Integer stockMinimo;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal precio;

    @NotNull(message = "Debes indicar el tipo de comercio")
    private TipoComercio tipoComercio;

    /**
     * Estado inicial. Opcional: si no se envia, el producto nace EN_BODEGA,
     * que es lo unico que tiene sentido para algo recien ingresado.
     */
    private EstadoProducto estado;
}
