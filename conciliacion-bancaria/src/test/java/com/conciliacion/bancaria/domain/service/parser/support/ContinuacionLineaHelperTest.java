package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaContinuacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContinuacionLineaHelper")
class ContinuacionLineaHelperTest {

    private final ContinuacionLineaHelper helper = new ContinuacionLineaHelper();

    private ReglaContinuacion regla() {
        ReglaContinuacion r = new ReglaContinuacion();
        r.setHabilitada(true);
        r.setCampoAncla("fecha");
        r.setCampoDestino("descripcion");
        return r;
    }

    @Test
    @DisplayName("linea sin fecha pero con solo descripcion es continuacion")
    void lineaSoloDescripcionEsContinuacion() {
        Map<String, String> valores = Map.of(
                "fecha", "",
                "oficina", "",
                "descripcion", "fc43188a43464proindus AS"
        );
        assertThat(helper.esContinuacion(valores, regla())).isTrue();
    }

    @Test
    @DisplayName("encabezado repetido (fecha vacia pero otras columnas con texto) NO es continuacion")
    void encabezadoRepetidoNoEsContinuacion() {
        Map<String, String> valores = Map.of(
                "fecha", "",
                "oficina", "Oficina",
                "descripcion", "Clase de Movimiento"
        );
        assertThat(helper.esContinuacion(valores, regla())).isFalse();
    }

    @Test
    @DisplayName("linea totalmente en blanco no es continuacion")
    void lineaEnBlancoNoEsContinuacion() {
        Map<String, String> valores = Map.of("fecha", "", "oficina", "", "descripcion", "");
        assertThat(helper.esContinuacion(valores, regla())).isFalse();
    }

    @Test
    @DisplayName("linea con fecha valida no es continuacion")
    void lineaConFechaNoEsContinuacion() {
        Map<String, String> valores = Map.of("fecha", "01", "oficina", "", "descripcion", "Abono");
        assertThat(helper.esContinuacion(valores, regla())).isFalse();
    }

    @Test
    @DisplayName("regla deshabilitada nunca detecta continuacion")
    void reglaDeshabilitada() {
        ReglaContinuacion r = regla();
        r.setHabilitada(false);
        Map<String, String> valores = Map.of("fecha", "", "descripcion", "algo");
        assertThat(helper.esContinuacion(valores, r)).isFalse();
    }
}
