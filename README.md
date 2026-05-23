# Conciliación Bancaria

Sistema de conciliación bancaria asistida desarrollado como proyecto de **Ingeniería de Software II**. Automatiza el emparejamiento entre movimientos bancarios y registros contables mediante un motor de coincidencia por rondas con puntuación de confianza.

---

## Arquitectura

El backend sigue **arquitectura hexagonal (ports & adapters)** con dominio puro sin dependencias de Spring:

```
adapter/in/web      → Controllers REST, DTOs
adapter/out/        → Persistencia JPA, parser CSV, logging
application/usecase → Orquestación de casos de uso
domain/             → Motor de conciliación, modelos, puertos, excepciones
config/             → Puente de inyección (SecurityConfig, JwtService, AsyncConfig)
```

El frontend es una SPA en **Angular 17** con Angular Material que consume la API REST.

---

## Requisitos

| Herramienta | Versión mínima |
|---|---|
| Java | 21 |
| Maven | 3.9+ (o usar `./mvnw`) |
| Docker + Docker Compose | 24+ |
| Node.js | 18+ |
| Angular CLI | 17.3 |

---

## Levantar el proyecto

### 1. Base de datos

```bash
cd conciliacion-bancaria
docker-compose up -d db
```

Esto levanta MariaDB 10.11 en `localhost:3306` con la base `conciliacion_db`.

### 2. Backend

```bash
cd conciliacion-bancaria
export DB_URL=jdbc:mariadb://localhost:3306/conciliacion_db
export DB_USER=conciliacion
export DB_PASSWORD=conciliacion
export JWT_SECRET=dev-secret-key-must-be-at-least-256-bits-long-for-hs256
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
ng serve
```

La UI queda disponible en `http://localhost:4200`.

---

## Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de MariaDB | `jdbc:mariadb://localhost:3306/conciliacion_db` |
| `DB_USER` | Usuario de base de datos | `conciliacion` |
| `DB_PASSWORD` | Contraseña de base de datos | `conciliacion` |
| `JWT_SECRET` | Clave HMAC-SHA256 (≥256 bits) | ver `.env.example` |

---

## Correr los tests

### Tests unitarios (dominio)

```bash
cd conciliacion-bancaria
./mvnw test -Dtest="ConciliationEngineTest,ConciliacionTest,CsvValidatorServiceTest,ClosureServiceTest"
```

### Tests de integración (requiere Docker)

```bash
cd conciliacion-bancaria
./mvnw test -Dtest="ConciliacionIntegrationTest"
```

Los tests de integración usan **Testcontainers**: levantan un contenedor MariaDB efímero automáticamente. Solo se necesita tener Docker corriendo.

### Suite completa + gate de cobertura

```bash
cd conciliacion-bancaria
./mvnw verify
```

El gate JaCoCo exige **≥ 80 % de cobertura de línea** en los paquetes del dominio. Si no se alcanza, el build falla.

### Tests del frontend

```bash
cd frontend
npm test -- --no-progress --watch=false --browsers=ChromeHeadless
```

---

## Flujo de conciliación

```
1. Crear conciliación (periodo YYYY-MM)   → estado: BORRADOR
2. Subir CSV extracto bancario            → procesado async (job polling)
3. Subir CSV auxiliar contable            → procesado async (job polling)
4. Motor ejecuta matching automático      → genera sugerencias con score de confianza
5. Revisar sugerencias (aceptar/rechazar) → estado: EN_REVISION
6. Justificar partidas no conciliadas
7. Cerrar conciliación                    → estado: CERRADA
```

### Rondas del motor de emparejamiento

| Ronda | Criterio | Confianza |
|---|---|---|
| 1 | Monto exacto + mismo tipo | 1.00 |
| 2 | Monto exacto + fecha ±3 días | 0.85 |
| 3 | Monto aproximado ±0.01 (redondeos) | 0.70 |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| `AUXILIAR` | Solo lectura |
| `CONTADOR` | Crear conciliaciones, subir CSVs, revisar sugerencias |
| `FINANZAS` | Lectura + cierre de conciliaciones |
| `ADMIN` | Acceso completo |

---

## CI/CD

El pipeline de GitHub Actions (`.github/workflows/ci.yml`) ejecuta en cada push/PR a `main`:

1. Compilación
2. Tests unitarios del dominio
3. Tests de integración (MariaDB como servicio)
4. `mvn verify` — suite completa + gate JaCoCo ≥ 80 %
5. SpotBugs — análisis estático (falla el build si hay hallazgos)
6. Tests Angular con ChromeHeadless
7. Publicación de reportes JaCoCo y Surefire como artefactos

---

## Desviaciones documentadas (ver SCOPE.md)

- **RT-02**: implementado con carga CSV en lugar de integración JDBC directa con el banco, por restricciones de acceso a APIs bancarias reales.
- **Tests k6**: excluidos por fuera del alcance del curso; justificado en SCOPE.md.
- **TLS local**: no configurado en desarrollo; el deploy en producción debe agregar un proxy inverso con TLS.
