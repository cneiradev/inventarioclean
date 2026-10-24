package com.inventarioclean.auditoria;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta de la evidencia de acciones.
 *
 * <p>Solo ADMINISTRADOR: el documento dice que el registro es "de manera
 * interna", no algo que el operador o el comercial puedan ver.
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private static final int MAX_POR_PAGINA = 100;

    private final AuditoriaRepository auditoriaRepository;

    /**
     * Historial paginado, del mas reciente al mas antiguo.
     *
     * @param accion filtro opcional (REGISTRAR_RENTA, CREAR_USUARIO...)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Page<Auditoria> listar(
            @RequestParam(required = false) AccionAuditoria accion,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int porPagina) {

        PageRequest pageable = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(porPagina, 1), MAX_POR_PAGINA),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return accion == null
                ? auditoriaRepository.findAll(pageable)
                : auditoriaRepository.findByAccion(accion, pageable);
    }
}
