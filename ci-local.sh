#!/usr/bin/env bash
# ci-local.sh — Replica exactamente el CI de GitHub Actions antes del push
# Uso: ./ci-local.sh [--skip-frontend] [--skip-backend] [--skip-docker]
set -euo pipefail

# ─── Colores ────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; RESET='\033[0m'

# ─── Flags ──────────────────────────────────────────────────────────────────
SKIP_FRONTEND=false
SKIP_BACKEND=false
SKIP_DOCKER=false

for arg in "$@"; do
  case $arg in
    --skip-frontend) SKIP_FRONTEND=true ;;
    --skip-backend)  SKIP_BACKEND=true  ;;
    --skip-docker)   SKIP_DOCKER=true   ;;
  esac
done

# ─── Helpers ────────────────────────────────────────────────────────────────
step()  { echo -e "\n${BLUE}${BOLD}▶ $1${RESET}"; }
ok()    { echo -e "${GREEN}✔ $1${RESET}"; }
fail()  { echo -e "${RED}✖ $1${RESET}"; exit 1; }
warn()  { echo -e "${YELLOW}⚠ $1${RESET}"; }
ERRORS=()
soft_fail() { ERRORS+=("$1"); echo -e "${RED}✖ $1${RESET}"; }

# ─── Variables de entorno (igual que CI) ────────────────────────────────────
export DB_URL="jdbc:mariadb://localhost:3306/conciliacion_test"
export DB_USER="test"
export DB_PASSWORD="test"
export MARIADB_ROOT_PASSWORD="root"
export MARIADB_DATABASE="conciliacion_test"
export MARIADB_USER="test"
export MARIADB_PASSWORD="test"
export JWT_SECRET="ci-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm"
export SPRING_PROFILES_ACTIVE="test"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/conciliacion-bancaria"
FRONTEND_DIR="$SCRIPT_DIR/frontend"


# ─── Enriquecer PATH en Windows (GitHub Desktop usa un entorno mínimo) ───────
# Todo este bloque usa set +e para que ningún fallo de PowerShell mate el script.
# GitHub Desktop's embedded Git Bash no hereda el PATH completo del usuario.
set +e
PS_EXE=""
if command -v powershell.exe &>/dev/null 2>&1; then
  PS_EXE="powershell.exe"
elif [ -f "/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe" ]; then
  PS_EXE="/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"
fi
if [ -n "$PS_EXE" ]; then
  WIN_PATH=$("$PS_EXE" -NoProfile -NonInteractive -Command \
    "[System.Environment]::GetEnvironmentVariable('PATH','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('PATH','User')" \
    2>/dev/null | tr -d '\r' | tr ';' ':')
  if [ -n "$WIN_PATH" ]; then
    export PATH="$PATH:$WIN_PATH"
  fi
  if [ -z "${JAVA_HOME:-}" ]; then
    REG_JAVA=$("$PS_EXE" -NoProfile -NonInteractive -Command \
      "[System.Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')" \
      2>/dev/null | tr -d '\r')
    if [ -z "$REG_JAVA" ]; then
      REG_JAVA=$("$PS_EXE" -NoProfile -NonInteractive -Command \
        "[System.Environment]::GetEnvironmentVariable('JAVA_HOME','User')" \
        2>/dev/null | tr -d '\r')
    fi
    if [ -n "$REG_JAVA" ]; then
      export JAVA_HOME="$REG_JAVA"
    fi
  fi
fi
set -e

# Fallback: detectar JAVA_HOME desde la JVM en PATH (no falla si java no está)
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}/bin" ]; then
  if command -v java &>/dev/null 2>&1; then
    _jh=$(java -XshowSettings:all -version 2>&1 | sed -n 's/.*java\.home = //p' | head -1) || true
    [ -n "$_jh" ] && export JAVA_HOME="$_jh"
  fi
fi

echo -e "${BOLD}╔══════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║      CI Local — Conciliación Bancaria        ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════╝${RESET}"

# ════════════════════════════════════════════════════════════════════════════
#  BLOQUE BACKEND
# ════════════════════════════════════════════════════════════════════════════
if [ "$SKIP_BACKEND" = false ]; then

  # ── 1. Levantar MariaDB con Docker ────────────────────────────────────────
  step "Levantando MariaDB 10.11 (igual que CI)"

  if [ "$SKIP_DOCKER" = false ]; then
    if ! command -v docker &>/dev/null; then
      fail "Docker no está instalado. Instálalo o usa --skip-docker (los tests de integración fallarán)."
    fi

    # Auto-detectar si el puerto 3306 ya está en uso (p.ej. conciliacion_db corriendo)
    # Usamos /dev/tcp (disponible en bash/Git Bash) con un timeout corto
    if (echo >/dev/tcp/localhost/3306) 2>/dev/null; then
      warn "Puerto 3306 ya en uso — reutilizando MariaDB existente (omitiendo Docker)"
      SKIP_DOCKER=true
    # Fallback: verificar via Docker si algún contenedor usa ese puerto
    elif docker ps --format "{{.Ports}}" 2>/dev/null | grep -q "0.0.0.0:3306"; then
      warn "Puerto 3306 ocupado por otro contenedor — omitiendo Docker"
      SKIP_DOCKER=true
    fi
  fi

  if [ "$SKIP_DOCKER" = false ]; then
    # Detener contenedor previo si existe
    docker rm -f ci-mariadb-local 2>/dev/null || true

    docker run -d \
      --name ci-mariadb-local \
      -e MARIADB_ROOT_PASSWORD="$MARIADB_ROOT_PASSWORD" \
      -e MARIADB_DATABASE="$MARIADB_DATABASE" \
      -e MARIADB_USER="$MARIADB_USER" \
      -e MARIADB_PASSWORD="$MARIADB_PASSWORD" \
      -p 3306:3306 \
      mariadb:10.11 > /dev/null

    # Esperar a que MariaDB esté lista (mismo health-check del CI)
    echo -n "  Esperando MariaDB"
    for i in $(seq 1 30); do
      if docker exec ci-mariadb-local healthcheck.sh --connect --innodb_initialized &>/dev/null 2>&1; then
        echo -e " ${GREEN}lista${RESET}"
        break
      fi
      echo -n "."
      sleep 2
      if [ "$i" -eq 30 ]; then
        docker rm -f ci-mariadb-local 2>/dev/null || true
        fail "MariaDB no arrancó en 60s."
      fi
    done
    ok "MariaDB lista en puerto 3306"
  else
    warn "Docker omitido — asegúrate de tener MariaDB corriendo en localhost:3306"
  fi

  # ── 2. Compilar ───────────────────────────────────────────────────────────
  step "Compilando proyecto (mvn compile)"
  if ! mvn compile -f "$BACKEND_DIR/pom.xml" -q; then
    [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null || true
    fail "Compilación falló — el CI fallaría aquí."
  fi
  ok "Compilación exitosa"

  # ── 3. Tests unitarios ────────────────────────────────────────────────────
  step "Tests unitarios (ConciliationEngineTest, ConciliacionTest, CsvValidatorServiceTest)"
  if ! mvn test \
    -Dtest="ConciliationEngineTest,ConciliacionTest,CsvValidatorServiceTest" \
    -f "$BACKEND_DIR/pom.xml" -q; then
    [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null || true
    fail "Tests unitarios fallaron — el CI fallaría aquí."
  fi
  ok "Tests unitarios pasaron"

  # ── 4. Tests de integración ───────────────────────────────────────────────
  step "Tests de integración (ConciliacionIntegrationTest)"
  if ! mvn test \
    -Dtest="ConciliacionIntegrationTest" \
    -f "$BACKEND_DIR/pom.xml" -q; then
    [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null || true
    fail "Tests de integración fallaron — el CI fallaría aquí."
  fi
  ok "Tests de integración pasaron"

  # ── 5. mvn verify + cobertura JaCoCo ─────────────────────────────────────
  step "mvn verify — todos los tests + cobertura JaCoCo"
  if ! mvn verify -f "$BACKEND_DIR/pom.xml" -q; then
    [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null || true
    fail "mvn verify falló (tests o cobertura insuficiente) — el CI fallaría aquí."
  fi
  ok "verify pasó — cobertura JaCoCo OK"

  # ── 6. SpotBugs ───────────────────────────────────────────────────────────
  step "Análisis estático — SpotBugs"
  # SpotBugs 4.8.x solo soporta hasta Java 21 (class file v65).
  # Si el JDK local es más nuevo, la herramienta falla con "Unsupported class file
  # major version" aunque no haya bugs reales. En ese caso advertimos y continuamos;
  # CI usa Java 21 y ejecutará el análisis correctamente.
  LOCAL_JAVA_VER=$(java -version 2>&1 | sed 's/.*version "\([0-9]*\).*/\1/' | head -1)
  if [ -n "$LOCAL_JAVA_VER" ] && [ "$LOCAL_JAVA_VER" -gt 21 ] 2>/dev/null; then
    warn "Java $LOCAL_JAVA_VER detectado — SpotBugs 4.8.x solo soporta hasta Java 21."
    warn "Saltando SpotBugs local; CI lo ejecuta con Java 21."
  elif ! mvn spotbugs:check -f "$BACKEND_DIR/pom.xml" -q; then
    [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null || true
    fail "SpotBugs encontró errores — el CI fallaría aquí."
  else
    ok "SpotBugs sin errores"
  fi

  # ── Limpiar Docker ────────────────────────────────────────────────────────
  [ "$SKIP_DOCKER" = false ] && docker rm -f ci-mariadb-local 2>/dev/null && ok "MariaDB detenida" || true

fi  # fin SKIP_BACKEND

# ════════════════════════════════════════════════════════════════════════════
#  BLOQUE FRONTEND
# ════════════════════════════════════════════════════════════════════════════
if [ "$SKIP_FRONTEND" = false ]; then

  step "Frontend Angular — instalando dependencias (npm ci)"
  if ! npm ci --prefix "$FRONTEND_DIR" --silent; then
    fail "npm ci falló — el CI fallaría aquí."
  fi
  ok "Dependencias instaladas"

  step "Frontend Angular — ejecutando tests con ChromeHeadless"
  if ! npm test --prefix "$FRONTEND_DIR" -- \
    --no-progress --watch=false --browsers=ChromeHeadless --code-coverage; then
    fail "Tests Angular fallaron — el CI fallaría aquí."
  fi
  ok "Tests Angular pasaron"

fi  # fin SKIP_FRONTEND

# ════════════════════════════════════════════════════════════════════════════
#  RESUMEN FINAL
# ════════════════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════╗${RESET}"
if [ ${#ERRORS[@]} -eq 0 ]; then
  echo -e "${GREEN}${BOLD}║  ✔  TODOS LOS CHECKS PASARON — LISTO PARA PUSH  ║${RESET}"
else
  echo -e "${RED}${BOLD}║  ✖  FALLOS DETECTADOS — NO HAGAS PUSH AÚN       ║${RESET}"
  for e in "${ERRORS[@]}"; do echo -e "${RED}  • $e${RESET}"; done
fi
echo -e "${BOLD}╚══════════════════════════════════════════════╝${RESET}"

[ ${#ERRORS[@]} -eq 0 ] && exit 0 || exit 1
