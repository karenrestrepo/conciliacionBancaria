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
 * Test de integración contra la BD de tests (conciliacion_test en localhost:3306).
 *
 * Prerequisito: el contenedor Docker 'conciliacion_db' debe estar en ejecución.
 * La BD 'conciliacion_test' se crea con:
 *   docker exec conciliacion_db mariadb -u root -proot -e
 *     "CREATE DATABASE IF NOT EXISTS conciliacion_test;
 *      CREATE USER IF NOT EXISTS 'test'@'%' IDENTIFIED BY 'test';
 *      GRANT ALL PRIVILEGES ON conciliacion_test.* TO 'test'@'%';"
 *
 * Anteriormente usaba Testcontainers para levantar un MariaDB efímero, pero
 * Docker Desktop ≥ 4.x devuelve 400 en todas las estrategias de socket
 * (npipe y TCP) que usa la librería docker-java, bloqueando la validación
 * del entorno. Se migró a una BD de tests persistente en el mismo contenedor.
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
            conciliacionRepo.findAll().stream()
                    .filter(c -> periodoNoDuplicar.equals(c.getPeriodo()))
                    .forEach(c -> conciliacionRepo.deleteById(c.getId()));
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
    @DisplayName("login con credenciales inválidas retorna 401")
    void loginInvalido() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "contador@test.com", "password", "wrongpassword"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
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
        periodoNoDuplicar = "2088-01";
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", periodoNoDuplicar, "idCuenta", 1));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.periodo").value(periodoNoDuplicar))
                .andExpect(jsonPath("$.data.estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("no se puede crear dos conciliaciones para el mismo período y banco")
    void noDuplicarPeriodo() throws Exception {
        periodoNoDuplicar = "2030-" + String.format("%02d",
                (java.time.LocalDate.now().getDayOfMonth() % 12) + 1);
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", periodoNoDuplicar, "idCuenta", 1));

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
        // Body válido para que llegue hasta @PreAuthorize y devuelva 403
        // (si el body fuera inválido, Bean Validation devuelve 400 antes que Security)
        String body = objectMapper.writeValueAsString(Map.of("periodo", "2026-02", "idCuenta", 1));

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