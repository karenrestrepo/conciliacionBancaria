package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConfiguracionGastoBancarioEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.ConfiguracionGastoBancarioJpaRepository;
import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfiguracionGastoBancarioRepositoryAdapter
        implements ConfiguracionGastoBancarioRepositoryPort {

    private final ConfiguracionGastoBancarioJpaRepository jpaRepository;

    @Override
    public ConfiguracionGastoBancario guardar(ConfiguracionGastoBancario config) {
        return toDomain(jpaRepository.save(toEntity(config)));
    }

    @Override
    public Optional<ConfiguracionGastoBancario> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ConfiguracionGastoBancario> listarPorCuenta(Long idCuenta) {
        return jpaRepository.findByIdCuenta(idCuenta).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<String> listarDescripcionesActivas(Long idCuenta) {
        return jpaRepository.findDescripcionesActivasByIdCuenta(idCuenta);
    }

    @Override
    public void eliminar(Long id) {
        jpaRepository.deleteById(id);
    }

    private ConfiguracionGastoBancarioEntity toEntity(ConfiguracionGastoBancario c) {
        return ConfiguracionGastoBancarioEntity.builder()
                .id(c.getId())
                .idCuenta(c.getIdCuenta())
                .descripcion(c.getDescripcion())
                .activo(c.isActivo())
                .fechaCreacion(c.getFechaCreacion())
                .build();
    }

    private ConfiguracionGastoBancario toDomain(ConfiguracionGastoBancarioEntity e) {
        return ConfiguracionGastoBancario.builder()
                .id(e.getId())
                .idCuenta(e.getIdCuenta())
                .descripcion(e.getDescripcion())
                .activo(e.isActivo())
                .fechaCreacion(e.getFechaCreacion())
                .build();
    }
}
