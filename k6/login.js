import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    email: __ENV.TEST_EMAIL || 'contador@test.com',
    password: __ENV.TEST_PASSWORD || 'Test1234!',
  });

  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/v1/auth/login`, payload, params);

  check(res, {
    'status es 200': (r) => r.status === 200,
    'respuesta tiene token': (r) => {
      const body = JSON.parse(r.body);
      return body.success === true && body.data.token.length > 0;
    },
    'tiempo de respuesta < 500ms': (r) => r.timings.duration < 500,
  });

  sleep(1);
}
