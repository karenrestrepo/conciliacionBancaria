# Scripts de pruebas de performance — k6

Scripts de carga para los endpoints principales del módulo de conciliación bancaria.  
Deben ejecutarse contra un entorno de **staging** con datos reales, no contra producción.

## Requisitos

- [k6](https://k6.io/docs/get-started/installation/) instalado (`k6 version` debe responder)
- Backend corriendo y accesible en `BASE_URL`
- Usuario de prueba creado en la BD de staging

## Scripts disponibles

| Script | Endpoint probado | Umbral |
|---|---|---|
| `login.js` | `POST /api/v1/auth/login` | p(95) < 500 ms |
| `conciliaciones.js` | `GET /api/v1/conciliaciones` + `GET /api/v1/metricas/resumen` | p(95) < 800 ms |

## Ejecución

```bash
# Prueba de login
k6 run \
  -e BASE_URL=http://staging-host:8080 \
  -e TEST_EMAIL=contador@test.com \
  -e TEST_PASSWORD=Test1234! \
  k6/login.js

# Prueba de conciliaciones (requiere usuario autenticado)
k6 run \
  -e BASE_URL=http://staging-host:8080 \
  -e TEST_EMAIL=contador@test.com \
  -e TEST_PASSWORD=Test1234! \
  k6/conciliaciones.js
```

## Configuración de carga (por defecto)

| Parámetro | login.js | conciliaciones.js |
|---|---|---|
| Usuarios virtuales | 10 | 5 |
| Duración | 30 s | 30 s |
| Error rate máximo | < 1 % | < 1 % |

## Variables de entorno

| Variable | Descripción | Default |
|---|---|---|
| `BASE_URL` | URL base del backend | `http://localhost:8080` |
| `TEST_EMAIL` | Email del usuario de prueba | `contador@test.com` |
| `TEST_PASSWORD` | Contraseña del usuario de prueba | `Test1234!` |
