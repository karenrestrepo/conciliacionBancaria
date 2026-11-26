package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.UsuarioCreateRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.UsuarioResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.UsuarioUpdateRequest;
import com.conciliacion.bancaria.adapter.out.persistence.entity.EmpresaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioPermisoEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.EmpresaJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.shared.Permiso;
import com.conciliacion.bancaria.shared.Rol;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioJpaRepository usuarioRepo;
    private final EmpresaJpaRepository empresaRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponse>>> listar() {
        Long empresaId = getEmpresaId();
        List<UsuarioResponse> lista = usuarioRepo.findByEmpresaId(empresaId).stream()
                .filter(u -> u.getRol() != Rol.ADMIN) // no listar al propio admin
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    @GetMapping("/permisos-defaults/{rol}")
    public ResponseEntity<ApiResponse<Set<String>>> permisosDefaults(@PathVariable Rol rol) {
        if (rol == Rol.ADMIN) {
            throw new IllegalArgumentException("No se puede consultar permisos para ADMIN");
        }
        Set<String> permisos = Permiso.defaultsParaRol(rol).stream()
                .map(Permiso::name)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(ApiResponse.ok(permisos));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<UsuarioResponse>> crear(
            @Valid @RequestBody UsuarioCreateRequest request) {

        if (request.getRol() == Rol.ADMIN) {
            throw new IllegalArgumentException("No se puede crear otro administrador");
        }

        Long empresaId = getEmpresaId();

        if (usuarioRepo.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo ya está en uso");
        }

        EmpresaEntity empresa = empresaRepo.findById(empresaId)
                .orElseThrow(() -> new IllegalStateException("Empresa no encontrada"));

        Set<Permiso> permisos = resolverPermisos(request.getPermisos(), request.getRol());

        List<UsuarioPermisoEntity> permisoEntities = permisos.stream()
                .map(p -> UsuarioPermisoEntity.builder().permiso(p).build())
                .collect(Collectors.toList());

        UsuarioEntity usuario = UsuarioEntity.builder()
                .empresa(empresa)
                .nombre(request.getNombre())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(request.getRol())
                .activo(true)
                .permisos(permisoEntities)
                .build();

        usuario = usuarioRepo.save(usuario);
        return ResponseEntity.ok(ApiResponse.ok("Usuario creado", toResponse(usuario)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<UsuarioResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request) {

        Long empresaId = getEmpresaId();
        UsuarioEntity usuario = usuarioRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        if (usuario.getEmpresa() == null || !usuario.getEmpresa().getId().equals(empresaId)) {
            throw new IllegalArgumentException("No tiene permisos para modificar este usuario");
        }

        if (usuario.getRol() == Rol.ADMIN) {
            throw new IllegalArgumentException("No se puede modificar al administrador");
        }

        if (request.getRol() == Rol.ADMIN) {
            throw new IllegalArgumentException("No se puede asignar el rol ADMIN");
        }

        if (usuarioRepo.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new IllegalArgumentException("El correo ya está en uso");
        }

        usuario.setNombre(request.getNombre());
        usuario.setEmail(request.getEmail());
        usuario.setRol(request.getRol());
        usuario.setActivo(request.getActivo());

        if (StringUtils.hasText(request.getPassword())) {
            usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        Set<Permiso> permisos = resolverPermisos(request.getPermisos(), request.getRol());
        usuario.getPermisos().clear();
        permisos.stream()
                .map(p -> UsuarioPermisoEntity.builder().permiso(p).build())
                .forEach(usuario.getPermisos()::add);

        usuarioRepo.save(usuario);
        return ResponseEntity.ok(ApiResponse.ok("Usuario actualizado", toResponse(usuario)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long getEmpresaId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth.getDetails();
        if (!(details instanceof Long empresaId)) {
            throw new IllegalStateException("Token sin empresa asociada");
        }
        return empresaId;
    }

    private Set<Permiso> resolverPermisos(Set<Permiso> solicitados, Rol rol) {
        Set<Permiso> asignables = Permiso.asignables();
        if (solicitados == null || solicitados.isEmpty()) {
            // Si no se envían permisos, se usan los del rol por defecto
            Set<Permiso> defaults = Permiso.defaultsParaRol(rol);
            defaults.retainAll(asignables);
            return defaults;
        }
        // Filtrar permisos no asignables (ej. GESTIONAR_USUARIOS)
        solicitados.retainAll(asignables);
        return solicitados;
    }

    private UsuarioResponse toResponse(UsuarioEntity u) {
        Set<Permiso> permisos = u.getPermisos().stream()
                .map(UsuarioPermisoEntity::getPermiso)
                .collect(Collectors.toSet());
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .email(u.getEmail())
                .rol(u.getRol())
                .activo(u.getActivo())
                .tsCreacion(u.getTsCreacion())
                .permisos(permisos)
                .build();
    }
}
