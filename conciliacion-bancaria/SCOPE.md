# SCOPE.md — Alcance declarado

**Proyecto:** Módulo de Conciliación Bancaria Asistida  
**Equipo:** Manuela Aristizabal · Karen Restrepo · Leonardo Gallego · Víctor Ramírez  
**Versión:** 1.0.0  
**Referencia:** TRD v1.1 · PRD v0.1 · Design Doc v1.1

---

## Requisitos implementados

| Requisito | Descripción | Estado |
|-----------|-------------|--------|
| RT-01 | Carga y validación de extracto CSV bancario | ✅ Implementado |
| RT-01b | Carga y validación de libro auxiliar CSV (stub del sistema contable) | ✅ Implementado |
| RT-03 | Motor de conciliación asíncrono (agrupación por monto, frecuencias, proximidad fecha ±3 días) | ✅ Implementado |
| RT-03b | Patrón Polling: HTTP 202 + job_id + endpoint de status | ✅ Implementado |
| RT-04 | Máquina de estados BORRADOR → EN_REVISION → CERRADA en el dominio | ✅ Implementado |
| RT-05 | Cierre con validación de partidas + arrastre al siguiente período | ✅ Implementado |
| RT-06 | RBAC: AUXILIAR, CONTADOR, FINANZAS, ADMIN con segregación de funciones | ✅ Implementado |
| NFR-Seg | JWT con expiración 8h, refresh token 24h, BCrypt cost=12, protección IDOR | ✅ Implementado |
| NFR-Obs | 9 eventos de log estructurado JSON + tabla metricas_conciliacion | ✅ Implementado |
| NFR-DB | Flyway migraciones V1–V7, todas las tablas del TRD | ✅ Implementado |
| Frontend | Angular 17+: carga CSV, revisión sugerencias, historial, dashboard | ✅ Implementado |

## Requisito adaptado — Integración con sistema contable (RT-02)

**Requisito original (TRD RT-02):** El `AccountingIntegrationAdapter` consulta el sistema contable actual vía JDBC en solo lectura.

**Implementación en este entorno académico:** El sistema contable externo no está disponible en el entorno de desarrollo. En su lugar, el módulo acepta la carga manual de un segundo archivo CSV con el formato del libro auxiliar bancario (mismas columnas: fecha, descripcion, monto, tipo_movimiento).

**Justificación:** Esta decisión está alineada con el supuesto tecnológico del TRD §11.1: *"El sistema contable expone los movimientos del auxiliar bancario mediante una consulta SQL o una interfaz programática accesible desde el backend"*. La interfaz `AccountingPort` existe en el dominio. El `CsvAccountingAdapter` implementa esa interfaz, haciendo que la sustitución por el adaptador JDBC real sea un cambio de un solo archivo sin tocar el dominio ni el motor.

**Impacto:** Cero cambios en la arquitectura hexagonal ni en el motor de conciliación. El dominio es completamente agnóstico al origen de los movimientos contables.

## Requisitos excluidos del TRD (fuera de alcance V1 académico)

| Requisito excluido | Justificación |
|--------------------|---------------|
| Integración JDBC con sistema contable real | No disponible en entorno académico — ver sección anterior |
| TLS en desarrollo local | Se configura a nivel de Nginx en producción; desarrollo usa HTTP |
| Docker secrets en desarrollo | Se usan variables de entorno; Docker secrets se documentan para producción |
| Tests de performance con k6 | Scripts incluidos en `/k6`; ejecución requiere entorno de staging con datos reales |

---

> Este archivo es el primero que lee el evaluador según la Guía del Estudiante §06.
