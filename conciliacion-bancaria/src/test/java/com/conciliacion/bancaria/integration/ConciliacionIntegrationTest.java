package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.conciliacion.bancaria.shared.Rol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del ciclo completo de conciliación.
 *
 * <p>Prerequisito: el contenedor MariaDB debe estar corriendo antes de ejecutar
 * estos tests. Levantarlo con:</p>
 * <pre>docker-compose up -d db</pre>
 *
 * <p>Se eliminó la dependencia de Testcontainers para evitar problemas de
 * conectividad con el Docker Engine en entornos de desarrollo Windows.
 * El perfil "test" (application-test.properties) apunta al mismo contenedor
 * que usa el equipo en desarrollo local.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integración — ciclo completo de conciliación")
class ConciliacionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioJpaRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ConciliacionJpaRepository conciliacionRepo;

    private String tokenContador;
    private String tokenFinanzas;

    @BeforeEach
    void setUp() throws Exception {
        // Crear usuario contador de prueba (idempotente)
        if (!usuarioRepo.existsByEmail("contador@test.com")) {
            usuarioRepo.save(UsuarioEntity.builder()
                    .nombre("Contador Test")
                    .email("contador@test.com")
                    .passwordHash(passwordEncoder.encode("Test1234!"))
                    .rol(Rol.CONTADOR)
                    .activo(true)
                    .build());
        }

        // Crear usuario finanzas de prueba (idempotente)
        if (!usuarioRepo.existsByEmail("finanzas@test.com")) {
            usuarioRepo.save(UsuarioEntity.builder()
                    .nombre("Finanzas Test")
                    .email("finanzas@test.com")
                    .passwordHash(passwordEncoder.encode("Test1234!"))
                    .rol(Rol.FINANZAS)
                    .activo(true)
                    .build());
        }

        tokenContador = obtenerToken("contador@test.com", "Test1234!");
        tokenFinanzas = obtenerToken("finanzas@test.com", "Test1234!");
    }

    private String obtenerToken(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", email, "password", password));

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .path("data").path("token").asText();
    }

    private String periodoNoDuplicar;

    @AfterEach
    void tearDown() {
        if (periodoNoDuplicar != null) {
            conciliacionRepo.findByPeriodo(periodoNoDuplicar)
                    .ifPresent(c -> conciliacionRepo.deleteById(c.getId()));
            periodoNoDuplicar = null;
        }
    }

    // ── Tests de autenticación ────────────────────────────────────────────────

    @Test
    @DisplayName("login con credenciales válidas retorna token JWT")
    void loginValido() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "contador@test.com", "password", "Test1234!"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.rol").value("CONTADOR"));
    }

    @Test
    @DisplayName("login con credenciales inválidas retorna 404")
    void loginInvalido() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "contador@test.com", "password", "wrongpassword"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("endpoint protegido sin token retorna 403")
    void endpointSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/conciliaciones"))
                .andExpect(status().isForbidden());
    }

    // ── Tests de conciliación ─────────────────────────────────────────────────

    @Test
    @DisplayName("CONTADOR puede iniciar una conciliación")
    void iniciarConciliacion() throws Exception {
        // Usar un período único para evitar conflicto con datos previos en la BD
        String periodo = "2025-" + String.format("%02d", (int)(Math.random() * 12) + 1);
        String body = objectMapper.writeValueAsString(Map.of("periodo", periodo));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.periodo").value(periodo))
                .andExpect(jsonPath("$.data.estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("no se puede crear dos conciliaciones para el mismo período")
    void noDuplicarPeriodo() throws Exception {
        periodoNoDuplicar = "2030-" + String.format("%02d",
                (java.time.LocalDate.now().getDayOfMonth() % 12) + 1);
        String body = objectMapper.writeValueAsString(Map.of("periodo", periodoNoDuplicar));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("período con formato inválido retorna 400")
    void periodoFormatoInvalido() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("periodo", "2024/03"));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FINANZAS solo puede leer conciliaciones — no crearlas")
    void finanzasSoloLectura() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("periodo", "2026-02"));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenFinanzas)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CONTADOR puede listar conciliaciones")
    void listarConciliaciones() throws Exception {
        mockMvc.perform(get("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("obtener conciliación inexistente retorna 404")
    void obtenerConciliacionInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/conciliaciones/99999")
                        .header("Authorization", "Bearer " + tokenContador))
                .andExpect(status().isNotFound());
    }
}