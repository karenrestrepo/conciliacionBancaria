package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.Banco;
import com.conciliacion.bancaria.domain.port.in.BancoUseCase;
import com.conciliacion.bancaria.domain.port.out.BancoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BancoUseCaseImpl implements BancoUseCase {

    private final BancoRepositoryPort bancoRepo;

    @Override
    @Transactional
    public Banco crear(String nombre, String codigo, Long empresaId) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del banco no puede estar vacío");
        }
        if (bancoRepo.existePorNombreYEmpresa(nombre.trim(), empresaId)) {
            throw new IllegalStateException("Ya existe un banco con el nombre: " + nombre);
        }
        Banco nuevo = Banco.builder()
                .nombre(nombre.trim())
                .codigo(codigo != null ? codigo.trim() : null)
                .empresaId(empresaId)
                .activo(true)
                .build();
        return bancoRepo.guardar(nuevo);
    }

    @Override
    @Transactional(readOnly = true)
    public Banco obtenerPorId(Long id) {
        return bancoRepo.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Banco no encontrado: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Banco> listarActivos(Long empresaId) {
        return bancoRepo.listarActivosPorEmpresa(empresaId);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Banco banco = obtenerPorId(id);
        Banco desactivado = Banco.builder()
                .id(banco.getId())
                .nombre(banco.getNombre())
                .codigo(banco.getCodigo())
                .activo(false)
                .tsCreacion(banco.getTsCreacion())
                .build();
        bancoRepo.guardar(desactivado);
    }
}
