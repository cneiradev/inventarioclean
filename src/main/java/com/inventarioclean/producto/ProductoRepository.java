package com.inventarioclean.producto;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Producto por SKU, solo si esta activo. */
    Optional<Producto> findBySkuAndActivoTrue(String sku);

    /**
     * Productos cuyo disponible esta en o bajo su minimo.
     *
     * <p>Query explicita en vez de nombre derivado: comparar dos columnas
     * ("cantidadDisponible &lt;= stockMinimo") no se puede expresar con el
     * formato de nombre de metodo de Spring Data.
     */
    @Query("""
            SELECT COUNT(p) FROM Producto p
            WHERE p.activo = TRUE AND p.cantidadDisponible <= p.stockMinimo
            """)
    long contarStockBajo();

    /** Suma del disponible de todos los productos activos. */
    @Query("SELECT COALESCE(SUM(p.cantidadDisponible), 0) FROM Producto p WHERE p.activo = TRUE")
    long sumarDisponible();

    /** Suma de lo que salio de bodega y no volvio. */
    @Query("""
            SELECT COALESCE(SUM(p.cantidadTotal - p.cantidadDisponible), 0)
            FROM Producto p WHERE p.activo = TRUE
            """)
    long sumarReservado();

    /**
     * Bloquea la fila del producto para modificar el stock.
     *
     * <p>Traduce a un {@code SELECT ... FOR UPDATE}: si dos operadores
     * rented el mismo producto al mismo tiempo, el segundo espera a que el
     * primero termine en vez de leer el mismo stock disponible y rentar dos
     * veces lo que hay. Sin esto, la validacion de "hay stock" se haria
     * sobre un dato que ya quedo viejo.
     *
     * <p>Solo usar dentro de una transaccion.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdParaActualizar(@Param("id") Long id);

    /** Producto por SKU incluyendo los desactivados. Sirve para verificar
     *  que el soft delete dejo la fila en la base. */
    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);

    long countByActivoTrueAndTipoComercio(TipoComercio tipoComercio);

    /**
     * Listado paginado del inventario.
     *
     * <p>Filtra por tipo (VENTA / RENTA) y por texto libre. La busqueda
     * funciona sobre el SKU y el nombre, que es lo que el operador conoce.
     *
     * @param tipo    si es null, devuelve ambos tipos
     * @param texto   si es null o vacio, no filtra
     * @param soloBajo si es true, devuelve solo los que estan en stock bajo
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = TRUE
              AND (:tipo IS NULL OR p.tipoComercio = :tipo)
              AND (:texto IS NULL OR :texto = ''
                   OR UPPER(p.sku) LIKE UPPER(CONCAT('%', :texto, '%'))
                   OR UPPER(p.nombre) LIKE UPPER(CONCAT('%', :texto, '%')))
              AND (:soloBajo = FALSE OR p.cantidadDisponible <= p.stockMinimo)
            """)
    Page<Producto> buscar(@Param("tipo") TipoComercio tipo,
                          @Param("texto") String texto,
                          @Param("soloBajo") boolean soloBajo,
                          Pageable pageable);

    /** Busqueda puntual para el formulario del operador (SKU o nombre). */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = TRUE
              AND (UPPER(p.sku) = UPPER(:codigo)
                   OR UPPER(p.nombre) LIKE UPPER(CONCAT('%', :codigo, '%')))
            ORDER BY p.sku
            """)
    List<Producto> buscarPorCodigoONombre(@Param("codigo") String codigo);

    /** Productos en stock bajo, para el dashboard de los tres roles. */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = TRUE
              AND p.tipoComercio = :tipo
              AND p.cantidadDisponible <= p.stockMinimo
            ORDER BY p.cantidadDisponible ASC, p.nombre
            """)
    List<Producto> stockBajo(@Param("tipo") TipoComercio tipo);
}

