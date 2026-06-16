package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;
import com.conciliacion.bancaria.domain.port.in.GastoBancarioUseCase;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GastoBancarioUseCaseImpl implements GastoBancarioUseCase {

    private final ConfiguracionGastoBancarioRepositoryPort repo;

    @Override
    @Transactional
    public ConfiguracionGastoBancario agregar(Long idCuenta, String descripcion) {
        ConfiguracionGastoBancario nueva = ConfiguracionGastoBancario.builder()
                .idCuenta(idCuenta)
                .descripcion(descripcion.trim())
                .activo(true)
                .build();
        return repo.guardar(nueva);
    }

    @Override
    public List<ConfiguracionGastoBancario> listar(Long idCuenta) {
        return repo.listarPorCuenta(idCuenta);
    }

    @Override
    @Transactional
    public void eliminar(Long idCuenta, Long id) {
        repo.buscarPorId(id).ifPresent(c -> {
            if (!c.getIdCuenta().equals(idCuenta)) {
                throw new IllegalArgumentException("El gasto no pertenece a esta cuenta");
            }
            repo.eliminar(id);
        });
    }
}
