package com.inventarioclean.dashboard;

import com.inventarioclean.dashboard.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicadores del panel principal.
 *
 * <p>Lo ven el administrador y el operador: el primero para reponer stock y
 * administrar el catalogo, el segundo para saber que salio y que falta.
 *
 * <p>El COMERCIAL no lo ve porque el documento lo describe como un rol de
 * solo consulta del inventario, sin dashboard. Si el cliente lo quiere, se
 * agrega al @PreAuthorize y nada mas.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    public ResponseEntity<DashboardResponse> obtener() {
        return ResponseEntity.ok(dashboardService.obtener());
    }
}
