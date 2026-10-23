package com.inventarioclean.producto;

import com.inventarioclean.common.security.UsuarioPrincipal;
import com.inventarioclean.producto.dto.ProductoRequest;
import com.inventarioclean.producto.dto.ProductoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de inventario.
 *
 * <p><b>Permisos, segun el documento:</b>
 * <ul>
 *   <li>ADMINISTRADOR: CRUD completo (crear, modificar, eliminar).</li>
 *   <li>OPERADOR y COMERCIAL: solo lectura del inventario. El operador
 *       registra renta/venta en la Fase 3, no edita el catalogo.</li>
 * </ul>
 *
 * <p>El COMERCIAL "solo puede ingresar a ver inventarios", asi que queda fuera
 * de cualquier escritura. El operador puede ver el inventario pero no
 * modificarlo: corregir el catalogo es tarea del administrador.
 */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    /** Tope de paginacion: evita que se pida la tabla entera de una vez. */
    private static final int MAX_POR_PAGINA = 100;

    private final ProductoService productoService;

    /**
     * Listado del inventario, disponible para los tres roles.
     *
     * @param tipo    VENTA o RENTA; si se omite, ambos
     * @param texto   busqueda libre sobre SKU y nombre
     * @param soloBajo si es true, solo productos en stock bajo
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR', 'COMERCIAL')")
    public ResponseEntity<Page<ProductoResponse>> listar(
            @RequestParam(required = false) TipoComercio tipo,
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "false") boolean soloBajo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int porPagina) {

        PageRequest pageable = PageRequest.of(
                Math.max(pagina, 0),
                Math.min(Math.max(porPagina, 1), MAX_POR_PAGINA),
                Sort.by("nombre"));

        return ResponseEntity.ok(productoService.listar(tipo, texto, soloBajo, pageable));
    }

    /** Dashboard: productos en stock bajo. */
    @GetMapping("/stock-bajo")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR', 'COMERCIAL')")
    public ResponseEntity<List<ProductoResponse>> stockBajo(
            @RequestParam(required = false) TipoComercio tipo) {

        // El documento pide mostrar los de VENTA; si no se indica tipo, se
        // usan los de venta para que el dashboard signifique algo.
        return ResponseEntity.ok(
                productoService.stockBajo(tipo == null ? TipoComercio.VENTA : tipo));
    }

    /**
     * Busqueda puntual por SKU o nombre. La usa el operador para localizar
     * el producto al registrar una renta o venta.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR', 'COMERCIAL')")
    public ResponseEntity<List<ProductoResponse>> buscar(@RequestParam String codigo) {
        return ResponseEntity.ok(productoService.buscarPorCodigoONombre(codigo));
    }

    @GetMapping("/sku/{sku}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR', 'COMERCIAL')")
    public ResponseEntity<ProductoResponse> buscarPorSku(@PathVariable String sku) {
        return ResponseEntity.ok(productoService.buscarPorSku(sku));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ProductoResponse> crear(
            @Valid @RequestBody ProductoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productoService.crear(request, auditor));
    }

    /** Modificar por SKU, como pide el documento ("por SKU/CodigoProducto"). */
    @PutMapping("/sku/{sku}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ProductoResponse> actualizar(
            @PathVariable String sku,
            @Valid @RequestBody ProductoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        return ResponseEntity.ok(productoService.actualizar(sku, request, auditor));
    }

    @DeleteMapping("/sku/{sku}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(
            @PathVariable String sku,
            @AuthenticationPrincipal UsuarioPrincipal auditor) {

        productoService.eliminar(sku, auditor);
        return ResponseEntity.noContent().build();
    }
}
