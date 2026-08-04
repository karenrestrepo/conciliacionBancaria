package com.conciliacion.bancaria.domain.model;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@With

public class Movimiento {

    private final Long id;
    private final LocalDate fecha;
    private final String descripcion;
    private final BigDecimal monto;
    private final String tipo;           // "DEBITO" o "CREDITO"
    private final EstadoMovimiento estado;
    /** Número de comprobante/documento del sistema contable de origen. Solo lo llenan
     * ciertos formatos de auxiliar (ej. SIESA); nulo para bancarios y para auxiliares
     * genéricos sin esa columna. Cuando está presente es una identidad más estable
     * que un hash de texto formateado para detectar duplicados entre recargas. */
    private final String numeroComprobante;
    /** Últimos 4 dígitos de la tarjeta de crédito, cuando el extracto trae ese dato en su
     * encabezado (formato ancho fijo, ver AnchoFijoBankStatementParser). Nulo para
     * movimientos contables y para extractos de cuenta bancaria normal sin tarjeta.
     * Permite distinguir a qué tarjetahabiente pertenece cada movimiento cuando varios
     * extractos se acumulan en una misma conciliación (auxiliar_conjunto). */
    private final String ultimosDigitosTarjeta;

    public Movimiento marcarSugerido() {
        return this.withEstado(EstadoMovimiento.SUGERIDO);
    }

    public Movimiento marcarConciliado() {
        return this.withEstado(EstadoMovimiento.CONCILIADO);
    }
}
