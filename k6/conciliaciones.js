import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

function obtenerToken() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      email: __ENV.TEST_EMAIL || 'contador@test.com',
      password: __ENV.TEST_PASSWORD || 'Test1234!',
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  return JSON.parse(res.body).data.token;
}

export function setup() {
  return { token: obtenerToken() };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.token}`,
    },
  };

  const resList = http.get(`${BASE_URL}/api/v1/conciliaciones`, params);
  check(resList, {
    'listar: status 200': (r) => r.status === 200,
    'listar: respuesta válida': (r) => JSON.parse(r.body).success === true,
    'listar: tiempo < 800ms': (r) => r.timings.duration < 800,
  });

  const resMetricas = http.get(`${BASE_URL}/api/v1/metricas/resumen`, params);
  check(resMetricas, {
    'métricas: status 200': (r) => r.status === 200,
    'métricas: tiempo < 800ms': (r) => r.timings.duration < 800,
  });

  sleep(1);
}
