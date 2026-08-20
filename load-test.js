import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('error_rate');

const API_URL  = __ENV.API_URL;
const ID_TOKEN = __ENV.ID_TOKEN;

export const options = {
  stages: [
    { duration: '1m',  target: 5  },  // slow ramp — warm up all 6 Lambda functions
    { duration: '1m',  target: 25 },  // gradual increase
    { duration: '1m',  target: 50 },  // reach peak
    { duration: '1m',  target: 50 },  // hold at peak — real test
    { duration: '30s', target: 0  },  // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    error_rate: ['rate<0.01'],      // 1% — realistic for serverless
  },
};

export default function () {
  const headers = {
    Authorization: ID_TOKEN,
    'Content-Type': 'application/json',
  };

  // POST
  const createRes = http.post(
    `${API_URL}/api/v1/tasks`,
    JSON.stringify({ title: `Load test ${Date.now()}`, status: 'TODO' }),
    { headers }
  );
  const created = check(createRes, {
    'POST /tasks → 201': (r) => r.status === 201,
    'POST response has id': (r) => JSON.parse(r.body)?.id !== undefined,
    'POST response has userId': (r) => JSON.parse(r.body)?.userId !== undefined,
    'POST response has createdAt': (r) => JSON.parse(r.body)?.createdAt !== undefined,
  });
  errorRate.add(!created);
  if (!created) {
    console.error(`POST failed: ${createRes.status}`);
    sleep(1);
    return;
  }

  const taskId = JSON.parse(createRes.body).id;
  sleep(1);

  // GET list
  const listRes = http.get(`${API_URL}/api/v1/tasks?limit=10`, { headers });
  const listed = check(listRes, {
    'GET /tasks → 200': (r) => r.status === 200,
    'GET response is array': (r) => JSON.parse(r.body)?.items !== undefined,
  });
  errorRate.add(!listed);
  sleep(1);

  // GET by ID
  const getRes = http.get(`${API_URL}/api/v1/tasks/${taskId}`, { headers });
  const got = check(getRes, {
    'GET /tasks/{id} → 200': (r) => r.status === 200,
    'GET by ID returns correct task': (r) => JSON.parse(r.body)?.id === taskId,
  });
  errorRate.add(!got);
  sleep(1);

  // PUT
  const updateRes = http.put(
    `${API_URL}/api/v1/tasks/${taskId}`,
    JSON.stringify({ title: `Updated ${Date.now()}`, status: 'IN_PROGRESS' }),
    { headers }
  );
  const updated = check(updateRes, {
    'PUT /tasks/{id} → 200': (r) => r.status === 200,
    'PUT response status is IN_PROGRESS': (r) =>
      JSON.parse(r.body)?.status === 'IN_PROGRESS',
  });
  errorRate.add(!updated);
  sleep(1);

  // DELETE
  const deleteRes = http.del(
    `${API_URL}/api/v1/tasks/${taskId}`,
    null,
    { headers }
  );
  const deleted = check(deleteRes, {
    'DELETE /tasks/{id} → 200': (r) => r.status === 200,
    'DELETE response has deleted=true': (r) =>
      JSON.parse(r.body)?.deleted === true,
  });
  errorRate.add(!deleted);
  sleep(2);
}

export function handleSummary(data) {
  const dur    = data.metrics.http_req_duration;
  const reqs   = data.metrics.http_reqs;
  const failed = data.metrics.http_req_failed;

  const p50  = dur?.values['p(50)']?.toFixed(0)
            ?? dur?.values['med']?.toFixed(0) ?? 'N/A';
  const p95  = dur?.values['p(95)']?.toFixed(0) ?? 'N/A';
  const p99  = dur?.values['p(99)']?.toFixed(0)
            ?? dur?.values['max']?.toFixed(0) ?? 'N/A';
  const rps  = reqs?.values?.rate?.toFixed(2) ?? 'N/A';
  const errs = failed?.values?.rate != null
    ? (failed.values.rate * 100).toFixed(2) : '0.00';
  const total = reqs?.values?.count ?? 'N/A';

  const summary = `
╔══════════════════════════════════════════════════════════╗
║           TASK MANAGER API — LOAD TEST RESULTS           ║
╠══════════════════════════════════════════════════════════╣
║  Total Requests      ${String(total).padEnd(35)}║
║  Requests/sec        ${String(rps).padEnd(35)}║
║  Error Rate          ${String(errs + '%').padEnd(35)}║
╠══════════════════════════════════════════════════════════╣
║  Latency p50         ${String(p50 + 'ms').padEnd(35)}║
║  Latency p95         ${String(p95 + 'ms').padEnd(35)}║
║  Latency p99         ${String(p99 + 'ms').padEnd(35)}║
╠══════════════════════════════════════════════════════════╣
║  Peak Concurrent Users: 50                               ║
║  Test Duration: ~4.5 minutes                             ║
╚══════════════════════════════════════════════════════════╝

→ Resume: "Sustained 50 concurrent users at p95 ${p95}ms with ${errs}% error rate"
`;

  console.log(summary);
  return {
    'load-test-results.json': JSON.stringify(data, null, 2),
    stdout: summary,
  };
}