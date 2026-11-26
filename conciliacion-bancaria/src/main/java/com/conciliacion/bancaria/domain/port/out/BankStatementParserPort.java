package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;

import java.util.List;

// Puerto de salida para el parseo de extractos bancarios.
// Una única implementación (BankStatementParserDispatcher) despacha internamente
// por config.getTipoOrigen() a la estrategia correspondiente (delimitado / ancho
// fijo / excel) — el dominio y el use case no saben cuál corre, igual que con
// AccountingPort. Nunca hay ramas por nombre de banco.
public interface BankStatementParserPort {

    List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo);

    ResultadoCuadre validarCuadre(byte[] contenido, ConfiguracionExtractoDetalle config);
}
