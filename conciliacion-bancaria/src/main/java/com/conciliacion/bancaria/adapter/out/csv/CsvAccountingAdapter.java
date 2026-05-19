package com.conciliacion.bancaria.adapter.out.csv;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.port.out.AccountingPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// Implementación del puerto contable usando CSV cargado manualmente.
// Sustituye la conexión JDBC al sistema contable externo (ver SCOPE.md).
// El dominio llama a AccountingPort sin saber qué implementación está activa.
@Component
@RequiredArgsConstructor
public class CsvAccountingAdapter implements AccountingPort {

    private final MovimientoRepositoryPort movimientoRepo;

    @Override
    public List<Movimiento> obtenerMovimientos(Long idConciliacion) {
        // Los movimientos contables ya fueron persistidos cuando el usuario
        // cargó el CSV del libro auxiliar. Aquí simplemente los recuperamos.
        return movimientoRepo.buscarContablesPorConciliacion(idConciliacion);
    }
}
