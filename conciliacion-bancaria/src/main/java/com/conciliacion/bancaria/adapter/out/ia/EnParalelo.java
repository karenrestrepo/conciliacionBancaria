package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/** Lee varios bloques de un documento al tiempo y devuelve los resultados en el orden original. */
final class EnParalelo {

    /** Más lecturas simultáneas podrían superar los límites de velocidad de la API. */
    private static final int LECTURAS_SIMULTANEAS = 3;

    private EnParalelo() {
    }

    static <B, R> List<R> leer(List<B> bloques, Function<B, R> lectura) {
        if (bloques.size() == 1) {
            return List.of(lectura.apply(bloques.get(0)));
        }
        ExecutorService hilos = Executors.newFixedThreadPool(Math.min(LECTURAS_SIMULTANEAS, bloques.size()));
        try {
            List<Future<R>> pendientes = bloques.stream()
                    .map(bloque -> hilos.submit(() -> lectura.apply(bloque)))
                    .toList();
            List<R> resultados = new ArrayList<>();
            for (Future<R> pendiente : pendientes) {
                resultados.add(pendiente.get());
            }
            return resultados;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException causa) throw causa;
            throw new CsvValidationException("No se pudo leer el documento");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CsvValidationException("Se interrumpió la lectura");
        } finally {
            // Si un bloque falla, no se siguen pagando los que aún no han empezado.
            hilos.shutdownNow();
        }
    }
}
