package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.ResultadoPruebaConfiguracion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.port.in.BancoUseCase;
import com.conciliacion.bancaria.domain.port.in.ConfiguracionExtractoUseCase;
import com.conciliacion.bancaria.domain.port.out.BankStatementParserPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoRepositoryPort;
import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConfiguracionExtractoUseCaseImpl implements ConfiguracionExtractoUseCase {

    private static final int MAX_MOVIMIENTOS_PREVIEW = 50;

    private final ConfiguracionExtractoRepositoryPort configuracionExtractoRepo;
    private final BancoUseCase bancoUseCase;
    private final BankStatementParserPort bankStatementParserPort;

    @Override
    public ConfiguracionExtracto crear(ConfiguracionExtracto c) {
        rechazarSiPdf(c);
        // Verifica que el banco exista (lanza excepción si no)
        bancoUseCase.obtenerPorId(c.getIdBanco());

        ConfiguracionExtracto nueva = c.withActivo(c.getActivo() != null ? c.getActivo() : true);
        return configuracionExtractoRepo.guardar(nueva);
    }

    @Override
    public ConfiguracionExtracto actualizar(Long id, ConfiguracionExtracto c) {
        rechazarSiPdf(c);
        ConfiguracionExtracto existente = obtenerPorId(id);
        ConfiguracionExtracto actualizada = existente
                .withNombre(c.getNombre())
                .withTipoArchivo(c.getTipoArchivo())
                .withAplicaParaTodasLasCuentas(c.isAplicaParaTodasLasCuentas())
                .withIdsCuentas(c.getIdsCuentas())
                .withConfiguracionDetalle(c.getConfiguracionDetalle())
                .withActivo(c.getActivo() != null ? c.getActivo() : existente.getActivo());
        return configuracionExtractoRepo.guardar(actualizada);
    }

    /**
     * El enum TipoArchivoExtracto incluye PDF porque el frontend ya lo ofrecía como opción,
     * pero el motor de parseo no tiene (ni tiene previsto tener) una estrategia para PDF.
     * Se rechaza explícitamente en vez de dejarlo fallar más adelante al intentar parsear.
     */
    private void rechazarSiPdf(ConfiguracionExtracto c) {
        if (c.getTipoArchivo() == TipoArchivoExtracto.PDF) {
            throw new IllegalArgumentException(
                    "Los archivos PDF aún no están soportados por el motor de extractos. "
                    + "Use CSV, TXT, XLS o XLSX.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ConfiguracionExtracto obtenerPorId(Long id) {
        return configuracionExtractoRepo.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Configuración de extracto no encontrada: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfiguracionExtracto> listarPorBanco(Long idBanco) {
        return configuracionExtractoRepo.listarPorBanco(idBanco);
    }

    @Override
    public void eliminar(Long id) {
        obtenerPorId(id);
        configuracionExtractoRepo.eliminar(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoPruebaConfiguracion probarConfiguracion(byte[] archivo, ConfiguracionExtractoDetalle detalle,
                                                             String periodo) {
        List<Movimiento> movimientos = bankStatementParserPort.parsear(archivo, detalle, periodo);

        List<String> advertencias = new ArrayList<>();
        if (movimientos.isEmpty()) {
            advertencias.add("El archivo no produjo movimientos. Verifique la configuración, "
                    + "o si el extracto legítimamente no tiene transacciones en este período.");
        }

        boolean validarCuadre = detalle.getCuadre() != null && detalle.getCuadre().isHabilitada();
        ResultadoCuadre cuadre = validarCuadre
                ? bankStatementParserPort.validarCuadre(archivo, detalle)
                : ResultadoCuadre.deshabilitado();
        if (validarCuadre && !cuadre.isCuadra()) {
            advertencias.addAll(cuadre.getAdvertencias());
        }

        List<Movimiento> preview = movimientos.size() > MAX_MOVIMIENTOS_PREVIEW
                ? movimientos.subList(0, MAX_MOVIMIENTOS_PREVIEW)
                : movimientos;

        return ResultadoPruebaConfiguracion.builder()
                .movimientos(preview)
                .totalMovimientos(movimientos.size())
                .cuadre(cuadre)
                .advertencias(advertencias)
                .build();
    }
}
