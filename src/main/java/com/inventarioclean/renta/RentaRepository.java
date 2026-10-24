package com.inventarioclean.renta;

import com.inventarioclean.movimiento.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RentaRepository extends JpaRepository<Movimiento, Long> {
    // Repository for Movimiento entities
    // The buggy method findByCodigoRentaOrderByCreatedAtAsc was removed
    // as the propiedad codigoRenta no existe en la entidad Movimiento.
}
