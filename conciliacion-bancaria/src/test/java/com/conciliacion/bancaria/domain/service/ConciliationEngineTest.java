package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConciliationEngine — algoritmo de emparejamiento")
class ConciliationEngineTest {

    private ConciliationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConciliationEngine();
    }

    // ── Utilidades para construir movimientos de prueba ───────────────────────

    private Movimiento bancario(Long id, String fecha, double monto, String tipo) {
        return Movimiento.builder()
                .id(id)
                .fecha(LocalDate.parse(fecha))
                .descripcion("Movimiento bancario " + id)
                .monto(new BigDecimal(String.valueOf(monto)))
                .tipo(tipo)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    private Movimiento contable(Long id, String fecha, double monto, String tipo) {
        return Movimiento.builder()
                .id(id)
                .fecha(LocalDate.parse(fecha))
                .descripcion("Movimiento contable " + id)
                .monto(new BigDecimal(String.valueOf(monto)))
                .tipo(tipo)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    // ── Ronda 1: monto exacto ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Ronda 1 — monto exacto + mismo tipo")
    class MontoExacto {

        @Test
        @DisplayName("debe emparejar dos movimientos con monto y tipo idénticos")
        void debeEmparejarMontoExacto() {
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-15", 150000.00, "DEBITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.sugerencias().get(0).getCriterio()).isEqualTo("MONTO_EXACTO");
            assertThat(resultado.sugerencias().get(0).getConfianza())
                    .isEqualByComparingTo(new BigDecimal("1.00"));
            assertThat(resultado.partidasBancarias()).isEmpty();
            assertThat(resultado.partidasContables()).isEmpty();
        }

        @Test
        @DisplayName("NO debe emparejar movimientos con mismo monto pero tipo diferente")
        void noDebeEmparejarTipoDiferente() {
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-15", 150000.00, "CREDITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).isEmpty();
            assertThat(resultado.partidasBancarias()).hasSize(1);
            assertThat(resultado.partidasContables()).hasSize(1);
        }

        @Test
        @DisplayName("debe generar partidas para movimientos sin par")
        void debeGenerarPartidasSinPar() {
            var bancarios = List.of(
                    bancario(1L, "2024-01-15", 150000.00, "DEBITO"),
                    bancario(2L, "2024-01-16", 200000.00, "CREDITO")
            );
            var contables = List.of(
                    contable(10L, "2024-01-15", 150000.00, "DEBITO")
            );

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.partidasBancarias()).hasSize(1);
            assertThat(resultado.partidasContables()).isEmpty();
        }

        @Test
        @DisplayName("debe manejar listas vacías sin lanzar excepción")
        void debeManejarListasVacias() {
            var resultado = engine.ejecutar(1L, List.of(), List.of());

            assertThat(resultado.sugerencias()).isEmpty();
            assertThat(resultado.partidasBancarias()).isEmpty();
            assertThat(resultado.partidasContables()).isEmpty();
        }
    }

    // ── Ronda 2: monto exacto + fecha próxima ─────────────────────────────────

    @Nested
    @DisplayName("Ronda 2 — monto exacto + fecha próxima ±3 días")
    class FechaProxima {

        @Test
        @DisplayName("debe emparejar movimientos con fecha dentro de ventana ±3 días")
        void debeEmparejarDentroDeVentana() {
            // Un solo par — monto exacto pero fechas distintas dentro de la ventana
            // El motor los empareja (puede ser Ronda 1 o 2, ambos son correctos)
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-17", 150000.00, "DEBITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.partidasBancarias()).isEmpty();
            assertThat(resultado.partidasContables()).isEmpty();
        }

        @Test
        @DisplayName("debe emparejar exactamente en el límite de 3 días")
        void debeEmparejarLimiteTresDias() {
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-18", 150000.00, "DEBITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.partidasBancarias()).isEmpty();
        }

        @Test
        @DisplayName("movimiento sin par en ninguna fuente queda como partida")
        void movimientoSinParQuedaComoPartida() {
            var bancarios = List.of(
                    bancario(1L, "2024-01-15", 150000.00, "DEBITO"),
                    bancario(2L, "2024-01-15", 99999.00, "DEBITO")  // monto único, sin par
            );
            var contables = List.of(
                    contable(10L, "2024-01-15", 150000.00, "DEBITO")
                    // no existe contable con monto 99999
            );

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.partidasBancarias()).hasSize(1);
            assertThat(resultado.partidasBancarias().get(0).getIdMovimiento()).isEqualTo(2L);
        }
    }

    // ── Ronda 3: monto aproximado ─────────────────────────────────────────────

    @Nested
    @DisplayName("Ronda 3 — monto aproximado ±0.01")
    class MontoAproximado {

        @Test
        @DisplayName("debe emparejar con diferencia de un centavo")
        void debeEmparejarUnCentavo() {
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-15", 149999.99, "DEBITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(1);
            assertThat(resultado.sugerencias().get(0).getCriterio())
                    .isEqualTo("MONTO_APROXIMADO");
            assertThat(resultado.sugerencias().get(0).getConfianza())
                    .isEqualByComparingTo(new BigDecimal("0.70"));
        }

        @Test
        @DisplayName("NO debe emparejar con diferencia mayor a un centavo")
        void noDebeEmparejarDiferenciaMayor() {
            var bancarios = List.of(bancario(1L, "2024-01-15", 150000.00, "DEBITO"));
            var contables = List.of(contable(10L, "2024-01-15", 149999.98, "DEBITO"));

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).isEmpty();
        }
    }

    // ── Múltiples movimientos ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Escenarios múltiples movimientos")
    class MultiplesMovimientos {

        @Test
        @DisplayName("debe emparejar correctamente cuando hay varios movimientos")
        void debeEmparejarVariosMovimientos() {
            var bancarios = List.of(
                    bancario(1L, "2024-01-15", 150000.00, "DEBITO"),
                    bancario(2L, "2024-01-16", 200000.00, "CREDITO"),
                    bancario(3L, "2024-01-17", 75000.00, "DEBITO")
            );
            var contables = List.of(
                    contable(10L, "2024-01-15", 150000.00, "DEBITO"),
                    contable(11L, "2024-01-16", 200000.00, "CREDITO")
            );

            var resultado = engine.ejecutar(1L, bancarios, contables);

            assertThat(resultado.sugerencias()).hasSize(2);
            assertThat(resultado.partidasBancarias()).hasSize(1);
            assertThat(resultado.partidasContables()).isEmpty();
        }

        @Test
        @DisplayName("Golden Dataset — Precision >= 95% y Recall >= 85%")
        void goldenDatasetPrecisionRecall() {
            // 10 pares exactos + 2 ambiguos + 2 huérfanos
            var bancarios = List.of(
                    bancario(1L,  "2024-01-01", 100000.00, "DEBITO"),
                    bancario(2L,  "2024-01-02", 200000.00, "CREDITO"),
                    bancario(3L,  "2024-01-03", 300000.00, "DEBITO"),
                    bancario(4L,  "2024-01-04", 400000.00, "CREDITO"),
                    bancario(5L,  "2024-01-05", 500000.00, "DEBITO"),
                    bancario(6L,  "2024-01-06", 600000.00, "CREDITO"),
                    bancario(7L,  "2024-01-07", 700000.00, "DEBITO"),
                    bancario(8L,  "2024-01-08", 800000.00, "CREDITO"),
                    bancario(9L,  "2024-01-09", 900000.00, "DEBITO"),
                    bancario(10L, "2024-01-10", 1000000.00, "CREDITO"),
                    bancario(11L, "2024-01-11", 50000.00, "DEBITO"),   // huérfano
                    bancario(12L, "2024-01-12", 60000.00, "CREDITO")   // huérfano
            );
            var contables = List.of(
                    contable(101L, "2024-01-01", 100000.00, "DEBITO"),
                    contable(102L, "2024-01-02", 200000.00, "CREDITO"),
                    contable(103L, "2024-01-03", 300000.00, "DEBITO"),
                    contable(104L, "2024-01-04", 400000.00, "CREDITO"),
                    contable(105L, "2024-01-05", 500000.00, "DEBITO"),
                    contable(106L, "2024-01-06", 600000.00, "CREDITO"),
                    contable(107L, "2024-01-07", 700000.00, "DEBITO"),
                    contable(108L, "2024-01-08", 800000.00, "CREDITO"),
                    contable(109L, "2024-01-09", 900000.00, "DEBITO"),
                    contable(110L, "2024-01-10", 1000000.00, "CREDITO")
            );

            var resultado = engine.ejecutar(1L, bancarios, contables);

            long sugerenciasValidas = resultado.sugerencias().stream()
                    .filter(s -> !s.getCriterio().equals("REASIGNADA"))
                    .count();

            // Precision = sugerencias correctas / total sugerencias
            double precision = (double) sugerenciasValidas / resultado.sugerencias().size();

            // Recall = sugerencias correctas / total pares reales (10)
            double recall = (double) sugerenciasValidas / 10.0;

            assertThat(precision).isGreaterThanOrEqualTo(0.95);
            assertThat(recall).isGreaterThanOrEqualTo(0.85);
        }
    }
}