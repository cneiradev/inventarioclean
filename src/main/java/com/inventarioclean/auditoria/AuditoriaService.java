package com.inventarioclean.auditoria;

import com.inventarioclean.common.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registro de evidencia de acciones.
 *
 * <p>El cliente pidio que toda accion de ADMINISTRADOR y OPERADOR quede
 * registrada con quien la hizo y cuando, para resolver dudas a futuro.
 *
 * <p><b>Decisiones de diseÃ±o:</b>
 * <ul>
 *   <li>Se guardan SIEMPRE en una transaccion nueva ({@code REQUIRES_NEW}).
 *       Asi la evidencia sobrevive aunque la operacion principal se revierta:
 *       si alguien intenta borrar un usuario y la transaccion falla, igual
 *       queda registrado que se intento. Un log que se pierde justo cuando
 *       hay un problema no sirve para nada.</li>
 *   <li>Si la escritura de la auditoria falla, NO se lanza excepcion: la
 *       accion del usuario ya ocurrio y|reportarla como fallida seria peor
 *       que perder el registro. Se avisa por log.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    /** limite de la columna detalle; se recorta si se excede. */
    private static final int MAX_DETALLE = 4000;

    private final AuditoriaRepository auditoriaRepository;

    /**
     * Registra una accion del usuario autenticado.
     *
     * @param principal usuario que ejecuta la accion (puede ser null en
     *                  acciones de sistema, como el bootstrap del admin)
     * @param accion    que se hizo
     * @param entidad   tipo de entidad afectada ("usuario", "producto"...)
     * @param entidadId id de la entidad afectada
     * @param detalle   informacion extra (valores antes/despues, codigos)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UsuarioPrincipal auditor,
                          AccionAuditoria accion,
                          String entidad,
                          Long entidadId,
                          String detalle) {

        Auditoria registro = Auditoria.builder()
                .usuarioId(auditor == null ? null : auditor.getId())
                // El username se copia: si el usuario se borra o cambia de
                // nombre, la evidencia historica debe seguir siendo legible.
                .username(auditor == null ? "SISTEMA" : auditor.getUsername())
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .detalle(recortar(detalle))
                .ip(ipActual())
                .build();

        try {
            auditoriaRepository.save(registro);
        } catch (Exception e) {
            // No se propaga: la accion del usuario ya ocurrio.
            log.error("No se pudo registrar la auditoria de {} {}: {}",
                    accion, entidad, e.getMessage());
        }
    }

    /** Variante para acciones sin entidad concreta (login, logout). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(UsuarioPrincipal auditor,
                          AccionAuditoria accion,
                          String detalle) {
        registrar(auditor, accion, "sistema", null, detalle);
    }

    private String recortar(String detalle) {
        if (detalle == null) return null;
        return detalle.length() <= MAX_DETALLE
                ? detalle
                : detalle.substring(0, MAX_DETALLE - 3) + "...";
    }

    /**
     * IP del cliente, para poder descartar acciones de origen desconocido.
     * Null fuera de una peticion web (por ejemplo, desde un job programado).
     */
    private String ipActual() {
        var atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getRemoteAddr();
        }
        return null;
    }
}
