package com.conciliacion.bancaria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // Habilita ejecución asíncrona del motor de conciliación
public class BancariaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancariaApplication.class, args);
    }
}
