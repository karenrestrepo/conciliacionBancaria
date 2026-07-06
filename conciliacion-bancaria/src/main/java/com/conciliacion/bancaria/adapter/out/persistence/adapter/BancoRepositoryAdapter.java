package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.BancoEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.BancoJpaRepository;
import com.conciliacion.bancaria.domain.model.Banco;
import com.conciliacion.bancaria.domain.port.out.BancoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BancoRepositoryAdapter implements BancoRepositoryPort {

    private final BancoJpaRepository jpaRepository;

    @Override
    public Banco guardar(Banco banco) {
        return toDomain(jpaRepository.save(toEntity(banco)));
    }

    @Override
    public Optional<Banco> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Banco> listarActivosPorEmpresa(Long empresaId) {
        return jpaRepository.findByActivoTrueAndEmpresaId(empresaId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Banco> listarTodos() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existePorNombreYEmpresa(String nombre, Long empresaId) {
        return jpaRepository.existsByNombreAndEmpresaId(nombre, empresaId);
    }

    private Banco toDomain(BancoEntity e) {
        return Banco.builder()
                .id(e.getId())
                .empresaId(e.getEmpresaId())
                .nombre(e.getNombre())
                .codigo(e.getCodigo())
                .activo(e.getActivo())
                .tsCreacion(e.getTsCreacion())
                .build();
    }

    private BancoEntity toEntity(Banco b) {
        return BancoEntity.builder()
                .id(b.getId())
                .empresaId(b.getEmpresaId())
                .nombre(b.getNombre())
                .codigo(b.getCodigo())
                .activo(b.getActivo() != null ? b.getActivo() : true)
                .tsCreacion(b.getTsCreacion())
                .build();
    }
}
