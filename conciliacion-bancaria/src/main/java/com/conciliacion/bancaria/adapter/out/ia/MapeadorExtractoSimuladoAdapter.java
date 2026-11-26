package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.MapeoExtracto;
import com.conciliacion.bancaria.domain.port.out.MapeadorExtractoIaPort;

import java.util.List;

/**
 * Modo sin IA ni costo, para desarrollo: asume las columnas Fecha, Descripción y Valor con signo,
 * con los títulos en la primera fila. Siempre pide confirmación.
 */
public class MapeadorExtractoSimuladoAdapter implements MapeadorExtractoIaPort {

    @Override
    public MapeoExtracto mapear(String muestra, String periodo) {
        return MapeoExtracto.builder()
                .tipoProducto("CUENTA").hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnasDescripcion(List.of(1))
                .esquemaMonto("MONTO_CON_SIGNO").columnaMonto(2).positivoEsAbono(true)
                .separadorDecimal(".").ordenFecha("DMA")
                .build();
    }

    @Override
    public boolean esSimulado() {
        return true;
    }
}
