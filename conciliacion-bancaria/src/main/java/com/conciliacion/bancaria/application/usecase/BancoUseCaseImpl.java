package com.conciliacion.bancaria.application.usecase;

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
    public Banco crear(String nombre, String codigo) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del banco no puede estar vacío");
        }
        if (bancoRepo.existePorNombre(nombre.trim())) {
            throw new IllegalStateException("Ya existe un banco con el nombre: " + nombre);
        }
        Banco nuevo = Banco.builder()
                .nombre(nombre.trim())
                .codigo(codigo != null ? codigo.trim() : null)
                .activo(true)
                .build();
        return bancoRepo.guardar(nuevo);
    }

    @Override
    @Transactional(readOnly = true)
    public Banco obtenerPorId(Long id) {
        return bancoRepo.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Banco no encontrado: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Banco> listarActivos() {
        return bancoRepo.listarActivos();
    }
}
