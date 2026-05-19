package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Movimiento;

import java.util.List;

// Puerto de salida hacia el sistema contable.
// En producción: implementado por JdbcAccountingAdapter (conexión real).
// En este entorno: implementado por CsvAccountingAdapter (carga manual de CSV).
// El dominio y el motor no saben cuál de los dos se usa — eso es la Arquitectura Hexagonal.
public interface AccountingPort {

    List<Movimiento> obtenerMovimientos(Long idConciliacion);
}