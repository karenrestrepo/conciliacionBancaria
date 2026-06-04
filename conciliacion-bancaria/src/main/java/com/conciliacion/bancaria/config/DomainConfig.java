package com.conciliacion.bancaria.config;

import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.domain.service.ClosureService;
import com.conciliacion.bancaria.domain.service.ConciliationEngine;
import com.conciliacion.bancaria.domain.service.CsvValidatorService;
import com.conciliacion.bancaria.domain.service.SiesaXlsParserService;
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

    @Bean
    public ConciliationEngine conciliationEngine() {
        return new ConciliationEngine();
    }

    @Bean
    public ClosureService closureService(ConciliacionRepositoryPort conciliacionRepo,
                                         PartidaRepositoryPort partidaRepo) {
        return new ClosureService(conciliacionRepo, partidaRepo);
    }
}