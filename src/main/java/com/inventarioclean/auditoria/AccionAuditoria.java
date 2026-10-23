package com.inventarioclean.auditoria;

/**
 * Acciones que quedan con evidencia en la tabla de auditoria.
 *
 * <p>El cliente pidio explicitamente registrar "quien lo hizo" y "cuando"
 * para toda accion de ADMINISTRADOR y OPERADOR.
 */
public enum AccionAuditoria {

    // Usuarios
    CREAR_USUARIO,
    MODIFICAR_USUARIO,
    ELIMINAR_USUARIO,

    // Productos
    CREAR_PRODUCTO,
    MODIFICAR_PRODUCTO,
    ELIMINAR_PRODUCTO,

    // Operaciones de bodega
    AJUSTAR_STOCK
}

