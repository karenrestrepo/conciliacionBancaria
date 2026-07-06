package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioPermisoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioPermisoJpaRepository extends JpaRepository<UsuarioPermisoEntity, Long> {

    List<UsuarioPermisoEntity> findByUsuario_Id(Long usuarioId);

    void deleteByUsuario_Id(Long usuarioId);
}
