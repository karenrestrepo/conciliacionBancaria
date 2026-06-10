package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.port.in.BancoUseCase;
import com.conciliacion.bancaria.domain.port.in.ConfiguracionExtractoUseCase;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConfiguracionExtractoUseCaseImpl implements ConfiguracionExtractoUseCase {

    private final ConfiguracionExtractoRepositoryPort configuracionExtractoRepo;
    private final BancoUseCase bancoUseCase;

    @Override
    public ConfiguracionExtracto crear(ConfiguracionExtracto c) {
        // Verifica que el banco exista (lanza excepción si no)
        bancoUseCase.obtenerPorId(c.getIdBanco());

        ConfiguracionExtracto nueva = c.withActivo(c.getActivo() != null ? c.getActivo() : true);
        return configuracionExtractoRepo.guardar(nueva);
    }

    @Override
    public ConfiguracionExtracto actualizar(Long id, ConfiguracionExtracto c) {
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

    @Override
    @Transactional(readOnly = true)
    public ConfiguracionExtracto obtenerPorId(Long id) {
        return configuracionExtractoRepo.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
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
}
