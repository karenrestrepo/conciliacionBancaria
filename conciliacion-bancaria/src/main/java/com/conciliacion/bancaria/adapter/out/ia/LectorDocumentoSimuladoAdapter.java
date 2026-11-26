package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.port.out.LectorDocumentoAuxiliarIaPort;

import java.math.BigDecimal;
import java.util.List;

/** Modo sin IA ni costo, para desarrollo: devuelve datos inventados y siempre pide confirmación. */
public class LectorDocumentoSimuladoAdapter implements LectorDocumentoAuxiliarIaPort {

    @Override
    public AuxiliarLeido leer(ArchivoAuxiliar.Documento documento, String periodo) {
        return AuxiliarLeido.builder()
                .totalDebitos(new BigDecimal("500000"))
                .totalCreditos(new BigDecimal("200000"))
                .movimientos(List.of(
                        fila(periodo + "-05", "RC-SIM", "DATO SIMULADO ingreso", "500000", "0"),
                        fila(periodo + "-12", "CE-SIM", "DATO SIMULADO egreso", "0", "200000")))
                .build();
    }

    private AuxiliarLeido.Fila fila(String fecha, String comprobante, String descripcion,
                                    String debito, String credito) {
        return AuxiliarLeido.Fila.builder()
                .fecha(fecha).comprobante(comprobante).descripcion(descripcion)
                .debito(new BigDecimal(debito)).credito(new BigDecimal(credito))
                .build();
    }

    @Override
    public boolean esSimulado() {
        return true;
    }
}
