package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConfiguracionExtractoEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.BancoJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.ConfiguracionExtractoJpaRepository;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfiguracionExtractoRepositoryAdapter implements ConfiguracionExtractoRepositoryPort {

    private final ConfiguracionExtractoJpaRepository configuracionExtractoJpaRepository;
    private final BancoJpaRepository bancoJpaRepository;

    @Override
    public ConfiguracionExtracto guardar(ConfiguracionExtracto c) {
        return toDomain(configuracionExtractoJpaRepository.save(toEntity(c)));
    }

    @Override
    public Optional<ConfiguracionExtracto> buscarPorId(Long id) {
        return configuracionExtractoJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ConfiguracionExtracto> listarPorBanco(Long idBanco) {
        return configuracionExtractoJpaRepository.findByIdBanco(idBanco).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void eliminar(Long id) {
        configuracionExtractoJpaRepository.deleteById(id);
    }

    private ConfiguracionExtracto toDomain(ConfiguracionExtractoEntity e) {
        String nombreBanco = bancoJpaRepository.findById(e.getIdBanco())
                .map(b -> b.getNombre())
                .orElse(null);
        List<Long> idsCuentas = e.getIdsCuentas() != null
                ? new ArrayList<>(e.getIdsCuentas())
                : new ArrayList<>();
        return ConfiguracionExtracto.builder()
                .id(e.getId())
                .idBanco(e.getIdBanco())
                .nombreBanco(nombreBanco)
                .nombre(e.getNombre())
                .tipoArchivo(e.getTipoArchivo())
                .aplicaParaTodasLasCuentas(e.isAplicaParaTodasLasCuentas())
                .idsCuentas(idsCuentas)
                .configuracionDetalle(e.getConfiguracionDetalle())
                .activo(e.getActivo())
                .fechaCreacion(e.getFechaCreacion())
                .fechaModificacion(e.getFechaModificacion())
                .build();
    }

    private ConfiguracionExtractoEntity toEntity(ConfiguracionExtracto c) {
        return ConfiguracionExtractoEntity.builder()
                .id(c.getId())
                .idBanco(c.getIdBanco())
                .nombre(c.getNombre())
                .tipoArchivo(c.getTipoArchivo())
                .aplicaParaTodasLasCuentas(c.isAplicaParaTodasLasCuentas())
                .idsCuentas(c.getIdsCuentas() != null ? new HashSet<>(c.getIdsCuentas()) : new HashSet<>())
                .configuracionDetalle(c.getConfiguracionDetalle())
                .activo(c.getActivo() != null ? c.getActivo() : true)
                .fechaCreacion(c.getFechaCreacion())
                .fechaModificacion(c.getFechaModificacion())
                .build();
    }
}
