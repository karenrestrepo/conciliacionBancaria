package com.conciliacion.bancaria.config;

import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.domain.service.ClosureService;
import com.conciliacion.bancaria.domain.service.ConciliationEngine;
import com.conciliacion.bancaria.domain.service.CsvValidatorService;
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
import com.conciliacion.bancaria.domain.service.SiesaXlsParserService;
import com.conciliacion.bancaria.domain.service.parser.AnchoFijoBankStatementParser;
import com.conciliacion.bancaria.domain.service.parser.BankStatementParserDispatcher;
import com.conciliacion.bancaria.domain.service.parser.DelimitadoBankStatementParser;
import com.conciliacion.bancaria.domain.service.parser.ExcelBankStatementParser;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    // Registramos los servicios del dominio como beans de Spring
    // sin contaminar el dominio con anotaciones de Spring.
    // Config actúa como puente entre el dominio puro y el contexto de Spring.

    @Bean
    public CsvValidatorService csvValidatorService() {
        return new CsvValidatorService();
    }

    @Bean
    public SiesaXlsParserService siesaXlsParserService() {
        return new SiesaXlsParserService();
    }

    // ── Motor genérico de extractos bancarios (BankStatementParserPort) ────

    @Bean
    public MontoParser montoParser() {
        return new MontoParser();
    }

    @Bean
    public FechaResolver fechaResolver() {
        return new FechaResolver();
    }

    @Bean
    public SignoResolver signoResolver() {
        return new SignoResolver();
    }

    @Bean
    public ContinuacionLineaHelper continuacionLineaHelper() {
        return new ContinuacionLineaHelper();
    }

    @Bean
    public CuadreValidator cuadreValidator(MontoParser montoParser) {
        return new CuadreValidator(montoParser);
    }

    @Bean
    public LineasTextoReader lineasTextoReader() {
        return new LineasTextoReader();
    }

    @Bean
    public DelimitadoBankStatementParser delimitadoBankStatementParser(
            MontoParser montoParser, FechaResolver fechaResolver, SignoResolver signoResolver,
            CuadreValidator cuadreValidator, LineasTextoReader lineasTextoReader) {
        return new DelimitadoBankStatementParser(montoParser, fechaResolver, signoResolver, cuadreValidator, lineasTextoReader);
    }

    @Bean
    public AnchoFijoBankStatementParser anchoFijoBankStatementParser(
            MontoParser montoParser, FechaResolver fechaResolver, SignoResolver signoResolver,
            ContinuacionLineaHelper continuacionLineaHelper, CuadreValidator cuadreValidator,
            LineasTextoReader lineasTextoReader) {
        return new AnchoFijoBankStatementParser(montoParser, fechaResolver, signoResolver,
                continuacionLineaHelper, cuadreValidator, lineasTextoReader);
    }

    @Bean
    public ExcelBankStatementParser excelBankStatementParser(
            MontoParser montoParser, FechaResolver fechaResolver,
            ContinuacionLineaHelper continuacionLineaHelper, CuadreValidator cuadreValidator) {
        return new ExcelBankStatementParser(montoParser, fechaResolver, continuacionLineaHelper, cuadreValidator);
    }

    @Bean
    public BankStatementParserDispatcher bankStatementParserDispatcher(
            DelimitadoBankStatementParser delimitadoParser, AnchoFijoBankStatementParser anchoFijoParser,
            ExcelBankStatementParser excelParser) {
        return new BankStatementParserDispatcher(delimitadoParser, anchoFijoParser, excelParser);
    }

    @Bean
    public ConciliationEngine conciliationEngine() {
        return new ConciliationEngine();
    }

    @Bean
    public MovimientoReversionService movimientoReversionService(MovimientoRepositoryPort movimientoRepo,
                                                                   PartidaRepositoryPort partidaRepo) {
        return new MovimientoReversionService(movimientoRepo, partidaRepo);
    }

    @Bean
    public ClosureService closureService(ConciliacionRepositoryPort conciliacionRepo,
                                         PartidaRepositoryPort partidaRepo,
                                         MovimientoRepositoryPort movimientoRepo,
                                         ConfiguracionGastoBancarioRepositoryPort gastoRepo) {
        return new ClosureService(conciliacionRepo, partidaRepo, movimientoRepo, gastoRepo);
    }
}
