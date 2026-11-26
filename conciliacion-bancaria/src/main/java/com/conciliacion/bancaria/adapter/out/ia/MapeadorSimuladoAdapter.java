package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.conciliacion.bancaria.domain.port.out.MapeadorAuxiliarIaPort;

import java.util.List;

/**
 * Modo sin IA ni costo, para desarrollo: asume las columnas Fecha, Documento, Descripción,
 * Débitos y Créditos, con los títulos en la primera fila. Siempre pide confirmación.
 */
public class MapeadorSimuladoAdapter implements MapeadorAuxiliarIaPort {

    @Override
    public MapeoColumnas mapear(String muestra) {
        return MapeoColumnas.builder()
                .hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnaComprobante(1).columnasDescripcion(List.of(2))
                .esquemaMonto("DEBITO_CREDITO").columnaDebito(3).columnaCredito(4)
                .separadorDecimal(".").ordenFecha("DMA")
                .build();
    }

    @Override
    public boolean esSimulado() {
        return true;
    }
}
