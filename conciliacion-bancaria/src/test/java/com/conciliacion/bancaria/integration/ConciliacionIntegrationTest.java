package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.entity.UsuarioEntity;
import com.conciliacion.bancaria.shared.Rol;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Integración — ciclo completo de conciliación")
class ConciliacionIntegrationTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("conciliacion_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioJpaRepository usuarioRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenContador;
    private String tokenFinanzas;

    @BeforeEach
    void setUp() throws Exception {
        // Crear usuario contador de prueba
        if (!usuarioRepo.existsByEmail("contador@test.com")) {
            usuarioRepo.save(UsuarioEntity.builder()
                    .nombre("Contador Test")
                    .email("contador@test.com")
                    .passwordHash(passwordEncoder.encode("Test1234!"))
                    .rol(Rol.CONTADOR)
                    .activo(true)
                    .build());
        }

        // Crear usuario finanzas de prueba
        if (!usuarioRepo.existsByEmail("finanzas@test.com")) {
            usuarioRepo.save(UsuarioEntity.builder()
                    .nombre("Finanzas Test")
                    .email("finanzas@test.com")
                    .passwordHash(passwordEncoder.encode("Test1234!"))
                    .rol(Rol.FINANZAS)
                    .activo(true)
                    .build());
        }

        // Obtener tokens
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
    @DisplayName("endpoint protegido sin token retorna 401")
    void endpointSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/conciliaciones"))
                .andExpect(status().isUnauthorized());
    }

    // ── Tests de conciliación ─────────────────────────────────────────────────

    @Test
    @DisplayName("CONTADOR puede iniciar una conciliación")
    void iniciarConciliacion() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", "2024-03"));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.periodo").value("2024-03"))
                .andExpect(jsonPath("$.data.estado").value("BORRADOR"));
    }

    @Test
    @DisplayName("no se puede crear dos conciliaciones para el mismo período")
    void noDuplicarPeriodo() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", "2024-04"));

        // Primera creación
        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Segunda creación — debe fallar
        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("período con formato inválido retorna 400")
    void periodoFormatoInvalido() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", "2024/03"));

        mockMvc.perform(post("/api/v1/conciliaciones")
                        .header("Authorization", "Bearer " + tokenContador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FINANZAS solo puede leer conciliaciones — no crearlas")
    void finanzasSoloLectura() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("periodo", "2024-05"));

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