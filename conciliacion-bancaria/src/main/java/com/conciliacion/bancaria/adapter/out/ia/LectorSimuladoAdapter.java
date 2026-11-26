package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;

import java.math.BigDecimal;
import java.util.List;

/** Devuelve siempre el mismo extracto ficticio. Permite probar el flujo sin clave ni costo. */
public class LectorSimuladoAdapter implements LectorExtractoIaPort {

    @Override
    public LecturaExtracto leer(byte[] pdf, String periodo) {
        return LecturaExtracto.builder()
                .tipoProducto("CUENTA")
                .saldoAnterior(new BigDecimal("1000000.00"))
                .totalCreditos(new BigDecimal("500000.00"))
                .totalDebitos(new BigDecimal("200000.00"))
                .saldoFinal(new BigDecimal("1300000.00"))
                .movimientos(List.of(
                        new LecturaExtracto.MovimientoLeido(periodo + "-05", "ABONO EJEMPLO",
                                new BigDecimal("500000.00"), "CREDITO"),
                        new LecturaExtracto.MovimientoLeido(periodo + "-10", "PAGO PROVEEDOR EJEMPLO",
                                new BigDecimal("200000.00"), "DEBITO")))
                .build();
    }

    @Override
    public boolean esSimulado() {
        return true;
    }
}
