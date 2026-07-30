# Cómo dar de alta un banco/formato de extracto nuevo

**Regla de oro: si mañana llega un extracto de un banco nunca visto, la respuesta correcta es "se configura con el wizard", nunca "se escribe un parser nuevo".** Si te encuentras necesitando tocar código Java para soportar un layout de extracto, es un bug del motor genérico — repórtalo, no lo trabajes con un parser dedicado.

Este documento explica cómo configurar un extracto nuevo usando el wizard de **Bancos → Configuración de extractos**, sin escribir código.

## 1. Los 3 tipos de origen

Cuando creas una configuración, eliges un **tipo de archivo** (CSV/TXT/XLS/XLSX) y, para TXT, un **tipo de origen** (el layout estructural real del contenido):

| Tipo de origen | Cuándo usarlo | Ejemplo |
|---|---|---|
| **Delimitado** | El archivo tiene columnas separadas por un carácter (coma, punto y coma, tabulador) | CSV estándar, exportación de banco con columnas claras |
| **Ancho fijo** | El archivo es un reporte de impresión: cada línea tiene columnas en posiciones fijas de caracter, sin separador | Extractos TXT de Davivienda (ahorro, corriente, tarjeta, fondo) |
| **Excel** | El archivo es una hoja de cálculo XLS/XLSX | Extracto de otro banco en Excel |

Los archivos **XLS/XLSX siempre usan "Excel"**; los **CSV siempre usan "Delimitado"**; solo **TXT** requiere elegir entre los dos primeros.

## 2. Campos comunes a todos los tipos

- **Encoding**: UTF-8, ISO-8859-1 (usar si tildes/ñ aparecen como "?" o "�") o Windows-1252.
- **Factor de escala del monto**: 1 = pesos, 1000 = miles.
- **Separador de miles / decimal**: ej. Colombia usa punto miles y coma decimal; algunos extractos de Davivienda usan coma miles y punto decimal (revisar un valor real del archivo, ej. `$99,808,949.23`).

## 3. Modo "Ancho fijo" — columnas por posición, sin regex

Antes había que escribir una regex Java. Ahora:

1. Pega en el campo **"Línea de ejemplo"** una línea real de datos copiada del extracto (no el encabezado).
2. Por cada dato que quieras capturar, agrega una columna: elige el **campo lógico** (día, mes, año, fecha completa, descripción, monto, débito, crédito, signo, tipo) y la **posición de inicio/fin** (1 = primer caracter de la línea).
3. El wizard muestra en vivo el texto que se extraería de esa posición en la línea pegada — ajusta inicio/fin hasta que coincida con el dato real.
4. Elige la **convención de signo**:
   - **Sufijo**: el monto termina en `+`/`-` (ej. `2,364,110.00-`).
   - **Prefijo**: el monto empieza con `+`/`-`.
   - **Columnas separadas**: hay una columna de débito y otra de crédito.
   - **Columna literal**: hay una columna de texto `DEBITO`/`CREDITO` o `D`/`C`.
5. Fecha: si el extracto trae día y mes en columnas separadas (sin año, como los reportes de página), define columnas `dia` y `mes` — el año se toma del período de la conciliación. Si trae fecha completa en una sola columna, define una columna `fecha` y el formato (ej. `dd/MM/yyyy`).

### Transacciones multilínea

Si una descripción larga continúa en una segunda línea física (sin fecha propia), activa **"Transacciones multilínea"** y define:
- **Campo ancla**: normalmente `dia` — si esa columna está vacía en la línea, es candidata a continuación.
- **Campo destino**: normalmente `descripcion` — el texto de la línea de continuación se concatena ahí.

Una línea solo se trata como continuación si el campo ancla está vacío **y** todas las demás columnas configuradas también están vacías (salvo el campo destino). Esto evita que un encabezado de tabla repetido por paginación (que también trae la fecha vacía, pero sí trae texto en otras columnas) se cuele como continuación.

## 4. Validar cuadre antes de guardar

Activa **"Validar cuadre"** y escribe las etiquetas de texto tal como aparecen en el archivo (sin regex): "Saldo anterior", "Créditos", "Débitos", "Saldo final". El motor busca esas etiquetas en el archivo y toma el primer número que aparece después (en la misma línea de texto, o en la celda adyacente/misma columna de la fila siguiente para Excel) y valida que `saldo anterior + créditos − débitos = saldo final`.

## 5. Probar antes de guardar

En el paso "Detalle" del wizard, usa **"Probar con archivo de muestra"**: sube un extracto real (no hace falta guardarlo primero), y verás:
- Un preview de los primeros movimientos parseados.
- El resultado de cuadre (si lo activaste), con las 4 cifras y la diferencia si no cuadra.
- Advertencias (ej. "0 movimientos encontrados" — puede ser legítimo si el período no tuvo transacciones, como una tarjeta de crédito sin consumos).

Ajusta la configuración y vuelve a probar hasta que el preview y el cuadre se vean correctos, y solo entonces guarda.

## 6. Casos ya cubiertos como referencia

Los siguientes extractos reales fueron configurados con este motor, sin ningún código nuevo, y están versionados como fixtures de test (`src/test/resources/extractos-muestra/` y `src/test/resources/extractos-config/`, ejercitados por `ExtractosRealesIntegrationTest`):

- Davivienda cuenta de ahorro (TXT, ancho fijo, fecha día/mes, signo sufijo).
- Davivienda cuenta corriente (TXT, ancho fijo, con transacciones multilínea).
- Davivienda tarjeta de crédito (TXT, ancho fijo, extracto sin movimientos ese período).
- Davivienda fondo fiduciario (TXT, ancho fijo, extracto sin movimientos ese período, cuadre por saldo de unidades).
- Extracto de otro banco (Excel, con bloques de encabezado repetidos por paginación y filas vacías intercaladas).

## 7. Qué NO hacer

- No escribir un parser Java nuevo para un banco.
- No agregar ramas `if/else` por nombre de banco en `CargaCsvUseCaseImpl` ni en ningún otro lado.
- No pedirle al usuario de negocio que escriba regex.

Si un extracto real no se puede modelar con las 3 estrategias existentes (Delimitado / Ancho fijo / Excel) y sus reglas de continuación/cuadre, es una limitación real del motor a resolver ampliando el schema (`ConfiguracionExtractoDetalle`) y las estrategias — no un caso aislado para resolver con código específico de ese banco.
