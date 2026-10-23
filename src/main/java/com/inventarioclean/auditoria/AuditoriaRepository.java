package com.inventarioclean.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    /** Historial filtrado por tipo de accion. */
    Page<Auditoria> findByAccion(AccionAuditoria accion, Pageable pageable);
}

