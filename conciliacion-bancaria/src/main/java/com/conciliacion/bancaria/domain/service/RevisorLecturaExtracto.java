package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.RevisionLectura;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Verifica con reglas fijas lo que interpretó la IA: datos completos, fechas y cuadre de saldos. */
public class RevisorLecturaExtracto {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    public RevisionLectura revisar(LecturaExtracto lectura, String periodo) {
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        BigDecimal creditos = BigDecimal.ZERO;
        BigDecimal debitos = BigDecimal.ZERO;
        int fueraDePeriodo = 0;

        boolean esTarjeta = "TARJETA_CREDITO".equals(lectura.getTipoProducto());
        if (!esTarjeta && !"CUENTA".equals(lectura.getTipoProducto())) {
            errores.add("No se identificó si el extracto es de una cuenta o de una tarjeta de crédito");
        }

        List<LecturaExtracto.MovimientoLeido> movimientos =
                lectura.getMovimientos() == null ? List.of() : lectura.getMovimientos();
        if (movimientos.isEmpty()) {
            errores.add("No se encontraron movimientos en el extracto");
        }

        for (int i = 0; i < movimientos.size(); i++) {
            LecturaExtracto.MovimientoLeido m = movimientos.get(i);
            String prefijo = "Movimiento " + (i + 1) + ": ";
            if (m == null) {
                errores.add(prefijo + "está vacío");
                continue;
            }
            LocalDate fecha = leerFecha(m.getFecha());
            if (fecha == null) {
                errores.add(prefijo + "fecha ausente o inválida");
            } else if (!YearMonth.from(fecha).toString().equals(periodo)) {
                fueraDePeriodo++;
            }
            if (m.getDescripcion() == null || m.getDescripcion().isBlank()) {
                errores.add(prefijo + "falta la descripción");
            }
            boolean montoValido = m.getMonto() != null && m.getMonto().signum() > 0;
            if (!montoValido) {
                errores.add(prefijo + "el valor debe ser mayor que cero");
            }
            if ("CREDITO".equals(m.getTipo())) {
                if (montoValido) creditos = creditos.add(m.getMonto());
            } else if ("DEBITO".equals(m.getTipo())) {
                if (montoValido) debitos = debitos.add(m.getMonto());
            } else {
                errores.add(prefijo + "no se sabe si es débito o crédito");
            }
        }

        if (fueraDePeriodo > 0) {
            advertencias.add(fueraDePeriodo + " movimiento(s) tienen fecha fuera del período " + periodo);
        }

        BigDecimal saldoCalculado = null;
        BigDecimal diferencia = null;
        EstadoCuadre estado = EstadoCuadre.NO_VERIFICABLE;

        if (lectura.getSaldoAnterior() != null && lectura.getSaldoFinal() != null) {
            // En una tarjeta el saldo es deuda: los cargos (débitos) lo aumentan.
            BigDecimal variacion = esTarjeta ? debitos.subtract(creditos) : creditos.subtract(debitos);
            saldoCalculado = lectura.getSaldoAnterior().add(variacion);
            diferencia = saldoCalculado.subtract(lectura.getSaldoFinal()).abs();
            if (diferencia.compareTo(TOLERANCIA) <= 0) {
                estado = EstadoCuadre.CUADRA;
            } else if (esTarjeta) {
                advertencias.add("El saldo de la tarjeta no coincide con los movimientos (puede deberse a "
                        + "compras a cuotas). Compare los movimientos con el extracto antes de aprobar");
            } else {
                estado = EstadoCuadre.NO_CUADRA;
                advertencias.add("El saldo calculado no coincide con el saldo final del extracto");
            }
        } else {
            advertencias.add("El extracto no permite verificar el cuadre: falta el saldo anterior o el saldo final");
        }

        if (!esTarjeta) {
            if (difiere(lectura.getTotalCreditos(), creditos)) {
                estado = EstadoCuadre.NO_CUADRA;
                advertencias.add("La suma de créditos leídos no coincide con el total del extracto");
            }
            if (difiere(lectura.getTotalDebitos(), debitos)) {
                estado = EstadoCuadre.NO_CUADRA;
                advertencias.add("La suma de débitos leídos no coincide con el total del extracto");
            }
        }

        return RevisionLectura.builder()
                .lectura(lectura)
                .sumaCreditos(creditos)
                .sumaDebitos(debitos)
                .saldoCalculado(saldoCalculado)
                .diferencia(diferencia)
                .estadoCuadre(estado)
                .errores(List.copyOf(errores))
                .advertencias(List.copyOf(advertencias))
                .build();
    }

    private boolean difiere(BigDecimal totalDeclarado, BigDecimal suma) {
        return totalDeclarado != null && totalDeclarado.abs().subtract(suma).abs().compareTo(TOLERANCIA) > 0;
    }

    private LocalDate leerFecha(String texto) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
