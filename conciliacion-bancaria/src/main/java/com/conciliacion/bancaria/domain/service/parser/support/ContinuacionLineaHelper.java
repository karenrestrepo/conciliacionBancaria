package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaContinuacion;

import java.util.Map;

/**
 * Detecta si una línea/fila que no pudo interpretarse como movimiento válido
 * (sin fecha) es en realidad la continuación física de la descripción del
 * movimiento anterior, o si es ruido a descartar (encabezado repetido de
 * página, línea en blanco de relleno, pie de página).
 *
 * Regla: es continuación solo si el campo ancla (normalmente fecha) está vacío,
 * TODOS los demás campos configurados también están vacíos salvo el campo
 * destino, y el campo destino tiene contenido. Esto evita que un encabezado de
 * tabla repetido (que también trae la columna de fecha vacía/no numérica, pero
 * SÍ trae contenido en otras columnas como Oficina/Doc./Valor) se cuele como
 * continuación — se descarta como cualquier otra línea inválida.
 */
public class ContinuacionLineaHelper {

    public boolean esContinuacion(Map<String, String> valoresPorCampo, ReglaContinuacion regla) {
        if (regla == null || !regla.isHabilitada()) return false;
        if (valoresPorCampo == null || valoresPorCampo.isEmpty()) return false;

        String ancla = valoresPorCampo.get(regla.getCampoAncla());
        if (ancla != null && !ancla.trim().isEmpty()) return false;

        String destino = valoresPorCampo.get(regla.getCampoDestino());
        if (destino == null || destino.trim().isEmpty()) return false;

        for (Map.Entry<String, String> e : valoresPorCampo.entrySet()) {
            if (e.getKey().equals(regla.getCampoDestino())) continue;
            String valor = e.getValue();
            if (valor != null && !valor.trim().isEmpty()) return false;
        }
        return true;
    }
}
