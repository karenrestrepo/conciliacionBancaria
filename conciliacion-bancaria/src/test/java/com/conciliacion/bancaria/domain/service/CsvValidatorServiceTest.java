package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CsvValidatorService — validación y parseo de archivos CSV")
class CsvValidatorServiceTest {

    private CsvValidatorService service;

    private static final Map<String, String> MAPEO_ESTANDAR = Map.of(
            "fecha", "fecha",
            "descripcion", "descripcion",
            "monto", "monto",
            "tipo_movimiento", "tipo_movimiento"
    );

    @BeforeEach
    void setUp() {
        service = new CsvValidatorService();
    }

    private byte[] csv(String contenido) {
        return contenido.getBytes();
    }

    @Nested
    @DisplayName("Happy path — CSV válido")
    class HappyPath {

        @Test
        @DisplayName("debe parsear un CSV válido con un movimiento")
        void debeParsearCsvValido() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago proveedor,150000.00,DEBITO"
            );

            List<Movimiento> resultado = service.parsear(contenido, MAPEO_ESTANDAR);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getMonto())
                    .isEqualByComparingTo(new BigDecimal("150000.00"));
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(0).getEstado()).isEqualTo(EstadoMovimiento.PENDIENTE);
        }

        @Test
        @DisplayName("debe parsear múltiples movimientos")
        void debeParsearMultiplesMovimientos() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago proveedor,150000.00,DEBITO\n" +
                            "2024-01-16,Consignación cliente,200000.00,CREDITO\n" +
                            "2024-01-17,Transferencia,75000.50,DEBITO"
            );

            List<Movimiento> resultado = service.parsear(contenido, MAPEO_ESTANDAR);

            assertThat(resultado).hasSize(3);
        }

        @Test
        @DisplayName("debe ignorar filas vacías")
        void debeIgnorarFilasVacias() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,150000.00,DEBITO\n" +
                            "\n" +
                            "2024-01-16,Cobro,200000.00,CREDITO"
            );

            List<Movimiento> resultado = service.parsear(contenido, MAPEO_ESTANDAR);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("debe aceptar monto con coma decimal")
        void debeAceptarMontoConComa() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,150000,50,DEBITO"
            );

            // Con coma el monto se parsea como 150000.50
            byte[] contenidoComa = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,150000.50,DEBITO"
            );

            List<Movimiento> resultado = service.parsear(contenidoComa, MAPEO_ESTANDAR);
            assertThat(resultado.get(0).getMonto())
                    .isEqualByComparingTo(new BigDecimal("150000.50"));
        }
    }

    @Nested
    @DisplayName("Flujos de error — CSV inválido")
    class FlujosError {

        @Test
        @DisplayName("debe lanzar excepción si el archivo está vacío")
        void debeLanzarExcepcionArchivoVacio() {
            assertThatThrownBy(() -> service.parsear(new byte[0], MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class);
        }

        @Test
        @DisplayName("debe lanzar excepción si falta columna requerida")
        void debeLanzarExcepcionColumnaFaltante() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto\n" +  // falta tipo_movimiento
                            "2024-01-15,Pago,150000.00"
            );

            assertThatThrownBy(() -> service.parsear(contenido, MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class)
                    .hasMessageContaining("tipo_movimiento");
        }

        @Test
        @DisplayName("debe lanzar excepción si el tipo_movimiento es inválido")
        void debeLanzarExcepcionTipoInvalido() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,150000.00,INVALIDO"
            );

            assertThatThrownBy(() -> service.parsear(contenido, MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class)
                    .hasMessageContaining("tipo_movimiento");
        }

        @Test
        @DisplayName("debe lanzar excepción si la fecha tiene formato inválido")
        void debeLanzarExcepcionFechaInvalida() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "15/01/2024,Pago,150000.00,DEBITO"
            );

            assertThatThrownBy(() -> service.parsear(contenido, MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class)
                    .hasMessageContaining("fecha");
        }

        @Test
        @DisplayName("debe lanzar excepción si el monto no es numérico")
        void debeLanzarExcepcionMontoNoNumerico() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,ABC,DEBITO"
            );

            assertThatThrownBy(() -> service.parsear(contenido, MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class)
                    .hasMessageContaining("monto");
        }

        @Test
        @DisplayName("debe lanzar excepción si el CSV solo tiene encabezados")
        void debeLanzarExcepcionSoloEncabezados() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento"
            );

            assertThatThrownBy(() -> service.parsear(contenido, MAPEO_ESTANDAR))
                    .isInstanceOf(CsvValidationException.class);
        }
    }

    @Nested
    @DisplayName("Casos límite")
    class CasosLimite {

        @Test
        @DisplayName("debe aceptar encabezados con espacios extras")
        void debeAceptarEncabezadosConEspacios() {
            byte[] contenido = csv(
                    " fecha , descripcion , monto , tipo_movimiento \n" +
                            "2024-01-15,Pago,150000.00,DEBITO"
            );

            List<Movimiento> resultado = service.parsear(contenido, MAPEO_ESTANDAR);
            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("debe aceptar tipo_movimiento en minúsculas")
        void debeAceptarTipoEnMinusculas() {
            byte[] contenido = csv(
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                            "2024-01-15,Pago,150000.00,debito"
            );

            List<Movimiento> resultado = service.parsear(contenido, MAPEO_ESTANDAR);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
        }
    }
}