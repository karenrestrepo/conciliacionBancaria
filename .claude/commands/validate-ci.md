# Validar CI local antes de push

Antes de declarar que cualquier cambio está listo o que "no va a fallar",
**debes ejecutar el script de validación CI local** y reportar el resultado.

## Pasos obligatorios

1. Ejecuta el script desde la raíz del repositorio:
   ```bash
   bash ci-local.sh
   ```

2. Si el script termina con código de salida `0`: informa al usuario que
   **todos los checks pasaron** y el push es seguro.

3. Si el script termina con código de salida `1`: 
   - Muestra el error exacto que falló.
   - **NO digas que el cambio está listo.**
   - Corrige el problema y vuelve a ejecutar `ci-local.sh` hasta que pase.
   - Solo después confirma que el push es seguro.

## Regla estricta

**Nunca afirmes "el CI no va a fallar" sin haber ejecutado `ci-local.sh` 
y obtenido salida exitosa en esta sesión.**

Si el script no existe todavía, dile al usuario que lo instale primero
con las instrucciones del README.

## Flags disponibles

| Flag              | Uso                                              |
|-------------------|--------------------------------------------------|
| `--skip-frontend` | Omitir tests Angular (cambios solo en backend)   |
| `--skip-backend`  | Omitir tests Java (cambios solo en frontend)     |
| `--skip-docker`   | Omitir Docker (requiere MariaDB ya corriendo)    |

Ejemplo para cambio solo en backend:
```bash
bash ci-local.sh --skip-frontend
```
