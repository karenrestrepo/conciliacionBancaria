package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.model.extractoconfig.TipoOrigenExtracto;
import com.conciliacion.bancaria.domain.port.out.BankStatementParserPort;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Única implementación de {@link BankStatementParserPort}. Despacha por el
 * tipo estructural declarado en la propia configuración ({@code tipoOrigen}),
 * nunca por extensión de archivo ni por nombre de banco.
 */
@RequiredArgsConstructor
public class BankStatementParserDispatcher implements BankStatementParserPort {

    private final DelimitadoBankStatementParser delimitadoParser;
    private final AnchoFijoBankStatementParser anchoFijoParser;
    private final ExcelBankStatementParser excelParser;

    @Override
    public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
        return switch (requireTipoOrigen(config)) {
            case DELIMITADO -> delimitadoParser.parsear(contenido, config, periodo);
            case ANCHO_FIJO -> anchoFijoParser.parsear(contenido, config, periodo);
            case EXCEL -> excelParser.parsear(contenido, config, periodo);
        };
    }

    @Override
    public ResultadoCuadre validarCuadre(byte[] contenido, ConfiguracionExtractoDetalle config) {
        return switch (requireTipoOrigen(config)) {
            case DELIMITADO -> delimitadoParser.validarCuadre(contenido, config);
            case ANCHO_FIJO -> anchoFijoParser.validarCuadre(contenido, config);
            case EXCEL -> excelParser.validarCuadre(contenido, config);
        };
    }

    private TipoOrigenExtracto requireTipoOrigen(ConfiguracionExtractoDetalle config) {
        if (config == null || config.getTipoOrigen() == null) {
            throw new CsvValidationException(
                    "La configuración de extracto no define un tipo de origen (tipoOrigen).");
        }
        return config.getTipoOrigen();
    }
}
