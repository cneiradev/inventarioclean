package com.inventarioclean.dashboard;

import com.inventarioclean.auditoria.AccionAuditoria;
import com.inventarioclean.auditoria.Auditoria;
import com.inventarioclean.auditoria.AuditoriaRepository;
import com.inventarioclean.dashboard.dto.DashboardResponse;
import com.inventarioclean.producto.ProductoRepository;
import com.inventarioclean.producto.TipoComercio;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Indicadores del panel principal.
 *
 * <p>Las consultas son agregaciones simples sobre el catalogo y el
 * historial; con el volumen de una bodega no hacen falta mas joins. Si
 * algun dia el catalogo crece mucho, el punto a mirar es
 * {@code sumarDisponible} y {@code sumarReservado}, que recorren todos los
 * productos.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    /** Cantidad de productos en stock bajo que se listan en el panel. */
    private static final int MAX_STOCK_BAJO = 10;

    /** Cantidad de operaciones recientes que se listan. */
    private static final int MAX_OPERACIONES = 8;

    private final ProductoRepository productoRepository;
    private final AuditoriaRepository auditoriaRepository;

    @Transactional(readOnly = true)
    public DashboardResponse obtener() {
        // Se listan los productos de venta con stock bajo: es lo mas urgente
        // que el operador debe ver al abrir el panel.
        List<DashboardResponse.ItemStockBajo> stockBajo =
                productoRepository.findAll(Sort.by("cantidadDisponible", "nombre")).stream()
                        .filter(p -> p.isActivo() && p.esStockBajo())
                        .limit(MAX_STOCK_BAJO)
                        .map(p -> new DashboardResponse.ItemStockBajo(
                                p.getId(), p.getSku(), p.getNombre(),
                                p.getTipoComercio().name(),
                                p.getCantidadDisponible(), p.getStockMinimo()))
                        .toList();

        List<DashboardResponse.UltimaOperacion> operaciones =
                auditoriaRepository.findAll(
                                PageRequest.of(0, MAX_OPERACIONES,
                                        Sort.by(Sort.Direction.DESC, "createdAt")))
                        .getContent().stream()
                        .filter(a -> esOperacionDeBodega(a.getAccion()))
                        .map(a -> new DashboardResponse.UltimaOperacion(
                                etiquetaAccion(a.getAccion()),
                                referencia(a),
                                a.getUsername(),
                                FORMATO_FECHA.format(a.getCreatedAt())))
                        .toList();

        return new DashboardResponse(
                productoRepository.count(),
                productoRepository.countByActivoTrueAndTipoComercio(TipoComercio.VENTA),
                productoRepository.contarStockBajo(),
                productoRepository.sumarDisponible(),
                stockBajo,
                operaciones);
    }

    /** true si la accion es una operacion de bodega, no de administracion. */
    private boolean esOperacionDeBodega(AccionAuditoria accion) {
        return switch (accion) {
            case REGISTRAR_VENTA -> true;
            default -> false;
        };
    }

    private String etiquetaAccion(AccionAuditoria accion) {
        return switch (accion) {
            case REGISTRAR_VENTA -> "Venta";
            default -> accion.name();
        };
    }

    /**
     * Extrae el codigo legible de la accion (R-2026-0001, V-2026-0002) para
     * que el operador pueda ir directo a esa operacion desde el panel.
     */
    private String referencia(Auditoria a) {
        if (a.getDetalle() == null) return "";

        int inicio = a.getDetalle().indexOf("R-20");
        if (inicio < 0) inicio = a.getDetalle().indexOf("V-20");
        if (inicio < 0) return "";

        String desde = a.getDetalle().substring(inicio);
        int fin = desde.indexOf(' ');
        return fin > 0 ? desde.substring(0, fin) : desde;
    }
}
