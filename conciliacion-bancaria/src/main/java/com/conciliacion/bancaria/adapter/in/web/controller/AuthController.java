package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.LoginRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.LoginResponse;
import com.conciliacion.bancaria.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.conciliacion.bancaria.config.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioJpaRepository usuarioRepo;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        var usuario = usuarioRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(),
                usuario.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("Usuario inactivo");
        }

        String token = jwtService.generarToken(
                usuario.getEmail(), usuario.getRol().name());

        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(
                token,
                usuario.getEmail(),
                usuario.getRol().name(),
                28800000L
        )));
    }
}