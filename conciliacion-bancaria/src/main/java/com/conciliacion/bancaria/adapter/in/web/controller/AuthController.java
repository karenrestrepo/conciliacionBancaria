package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.*;
import com.conciliacion.bancaria.adapter.out.persistence.entity.EmpresaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioPermisoEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.EmpresaJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.conciliacion.bancaria.config.JwtService;
import com.conciliacion.bancaria.domain.exception.CredencialesInvalidasException;
import com.conciliacion.bancaria.shared.Permiso;
import com.conciliacion.bancaria.shared.Rol;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioJpaRepository usuarioRepo;
    private final EmpresaJpaRepository empresaRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        var usuario = usuarioRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new CredencialesInvalidasException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("Credenciales inválidas");
        }

        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("Usuario inactivo");
        }

        Long empresaId = usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null;
        String nombreEmpresa = usuario.getEmpresa() != null ? usuario.getEmpresa().getNombre() : null;

        List<String> permisosList = usuario.getPermisos().stream()
                .map(p -> p.getPermiso().name())
                .collect(Collectors.toList());

        // El admin semilla (empresa=null) recibe todos los permisos
        if (empresaId == null && usuario.getRol() == Rol.ADMIN) {
            permisosList = Set.of(Permiso.values()).stream()
                    .map(Permiso::name)
                    .collect(Collectors.toList());
        }

        String permisosStr = String.join(",", permisosList);
        String token = jwtService.generarToken(
                usuario.getEmail(), usuario.getRol().name(), empresaId, permisosStr);

        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(
                token,
                usuario.getEmail(),
                usuario.getRol().name(),
                empresaId,
                nombreEmpresa,
                permisosList,
                28800000L
        )));
    }

    /** Verifica si un NIT/Cédula ya tiene empresa registrada. */
    @PostMapping("/verificar-empresa")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verificarEmpresa(
            @Valid @RequestBody VerificarEmpresaRequest request) {

        boolean existe = empresaRepo.existsByTipoIdentificacionAndNumeroIdentificacion(
                request.getTipoIdentificacion(), request.getNumeroIdentificacion());

        return ResponseEntity.ok(ApiResponse.ok(Map.of("existe", existe)));
    }

    /** Registra una nueva empresa y su usuario administrador en una sola transacción. */
    @PostMapping("/registro")
    @Transactional
    public ResponseEntity<ApiResponse<LoginResponse>> registro(
            @Valid @RequestBody RegistroRequest request) {

        if (empresaRepo.existsByTipoIdentificacionAndNumeroIdentificacion(
                request.getTipoIdentificacion(), request.getNumeroIdentificacion())) {
            throw new IllegalArgumentException(
                    "Ya existe una empresa registrada con ese número de identificación");
        }

        if (usuarioRepo.existsByEmail(request.getEmailAdmin())) {
            throw new IllegalArgumentException(
                    "El correo del administrador ya está en uso");
        }

        // Crear empresa
        EmpresaEntity empresa = EmpresaEntity.builder()
                .tipoIdentificacion(request.getTipoIdentificacion())
                .numeroIdentificacion(request.getNumeroIdentificacion())
                .digitoVerificacion(request.getDigitoVerificacion())
                .nombre(request.getNombreEmpresa())
                .build();
        empresa = empresaRepo.save(empresa);

        // Crear admin
        UsuarioEntity admin = UsuarioEntity.builder()
                .empresa(empresa)
                .nombre(request.getNombreAdmin())
                .email(request.getEmailAdmin())
                .passwordHash(passwordEncoder.encode(request.getPasswordAdmin()))
                .rol(Rol.ADMIN)
                .activo(true)
                .build();

        // Crear permisos con back-reference al admin
        final UsuarioEntity adminRef = admin;
        List<UsuarioPermisoEntity> permisoEntities = Permiso.defaultsParaRol(Rol.ADMIN)
                .stream()
                .map(p -> UsuarioPermisoEntity.builder().permiso(p).usuario(adminRef).build())
                .collect(Collectors.toList());
        admin.setPermisos(permisoEntities);

        admin = usuarioRepo.save(admin);

        List<String> permisosList = admin.getPermisos().stream()
                .map(p -> p.getPermiso().name())
                .collect(Collectors.toList());

        String permisosStr = String.join(",", permisosList);
        String token = jwtService.generarToken(
                admin.getEmail(), Rol.ADMIN.name(), empresa.getId(), permisosStr);

        return ResponseEntity.ok(ApiResponse.ok("Empresa y administrador creados exitosamente",
                new LoginResponse(
                        token,
                        admin.getEmail(),
                        Rol.ADMIN.name(),
                        empresa.getId(),
                        empresa.getNombre(),
                        permisosList,
                        28800000L
                )));
    }
}
