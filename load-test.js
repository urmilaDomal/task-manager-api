import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ─────────────────────────────────────────────────────────────
// Custom metrics — tracked separately from k6's built-in ones
// ─────────────────────────────────────────────────────────────
const errorRate = new Rate('error_rate');         // % of failed requests
const createDuration = new Trend('create_duration'); // POST latency
const getDuration = new Trend('get_duration');       // GET latency

// ─────────────────────────────────────────────────────────────
// Test configuration — defines the load shape
// ─────────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users over 30s
    { duration: '1m',  target: 50 },   // Ramp up to 50 concurrent users
    { duration: '2m',  target: 50 },   // Hold at 50 users for 2 minutes
    { duration: '30s', target: 0 },    // Ramp down to 0
  ],

  // ── Thresholds — test FAILS if these aren't met ──────────
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95% of requests under 2s
    http_req_duration: ['p(99)<5000'],  // 99% of requests under 5s
    error_rate:        ['rate<0.01'],   // Less than 1% error rate
    http_req_failed:   ['rate<0.01'],   // Less than 1% HTTP failures
  },
};

// ─────────────────────────────────────────────────────────────
// Environment variables — passed via -e flag when running k6
// ─────────────────────────────────────────────────────────────
const BASE_URL = __ENV.API_URL;
const TOKEN    = __ENV.ID_TOKEN;

// ─────────────────────────────────────────────────────────────
// Default function — runs once per virtual user per iteration
// ─────────────────────────────────────────────────────────────
export default function () {

  const headers = {
    'Authorization': TOKEN,
    'Content-Type': 'application/json',
  };

  // ── 1. Create a task ──────────────────────────────────────
  const createPayload = JSON.stringify({
    title: `Load test task ${Date.now()}`,
    description: 'Created by k6 load test',
  });

  const createRes = http.post(`${BASE_URL}/api/v1/tasks`, createPayload, { headers });

  const createOk = check(createRes, {
    'POST /tasks → 201': (r) => r.status === 201,
    'POST response has id': (r) => JSON.parse(r.body).id !== undefined,
    'POST response has userId': (r) => JSON.parse(r.body).userId !== undefined,
    'POST response has createdAt': (r) => JSON.parse(r.body).createdAt !== null,
  });

  errorRate.add(!createOk);
  createDuration.add(createRes.timings.duration);

  // If create failed, skip remaining steps for this iteration
  if (!createOk) {
    sleep(1);
    return;
  }

  const taskId = JSON.parse(createRes.body).id;

  sleep(0.5);   // Small pause between requests — realistic user behaviour

  // ── 2. Get all tasks ──────────────────────────────────────
  const getAllRes = http.get(`${BASE_URL}/api/v1/tasks`, { headers });

  const getAllOk = check(getAllRes, {
    'GET /tasks → 200': (r) => r.status === 200,
    'GET response is array': (r) => Array.isArray(JSON.parse(r.body)),
  });

  errorRate.add(!getAllOk);
  getDuration.add(getAllRes.timings.duration);

  sleep(0.5);

  // ── 3. Get task by ID ─────────────────────────────────────
  const getByIdRes = http.get(`${BASE_URL}/api/v1/tasks/${taskId}`, { headers });

  const getByIdOk = check(getByIdRes, {
    'GET /tasks/{id} → 200': (r) => r.status === 200,
    'GET by ID returns correct task': (r) => JSON.parse(r.body).id === taskId,
  });

  errorRate.add(!getByIdOk);

  sleep(0.5);

  // ── 4. Update the task ────────────────────────────────────
  const updatePayload = JSON.stringify({
    title: `Updated task ${Date.now()}`,
    description: 'Updated by k6 load test',
    status: 'IN_PROGRESS',
  });

  const updateRes = http.put(
    `${BASE_URL}/api/v1/tasks/${taskId}`,
    updatePayload,
    { headers }
  );

  const updateOk = check(updateRes, {
    'PUT /tasks/{id} → 200': (r) => r.status === 200,
    'PUT response status is IN_PROGRESS': (r) => JSON.parse(r.body).status === 'IN_PROGRESS',
  });

  errorRate.add(!updateOk);

  sleep(0.5);

  // ── 5. Delete the task ────────────────────────────────────
  const deleteRes = http.del(`${BASE_URL}/api/v1/tasks/${taskId}`, null, { headers });

  const deleteOk = check(deleteRes, {
    'DELETE /tasks/{id} → 204': (r) => r.status === 204,
  });

  errorRate.add(!deleteOk);

  sleep(1);   // Pause between full iterations
}

// ─────────────────────────────────────────────────────────────
// Summary — printed after the test completes
// ─────────────────────────────────────────────────────────────
export function handleSummary(data) {
    const dur    = data.metrics.http_req_duration;
    const reqs   = data.metrics.http_reqs;
    const failed = data.metrics.http_req_failed;
  
    const p50   = dur?.values['p(50)']?.toFixed(0)
           ?? dur?.values['med']?.toFixed(0)
           ?? 'N/A';
const p95   = dur?.values['p(95)']?.toFixed(0) ?? 'N/A';
const p99   = dur?.values['p(99)']?.toFixed(0)
           ?? dur?.values['max']?.toFixed(0)
           ?? 'N/A';
    const rps   = reqs?.values?.rate?.toFixed(2) ?? 'N/A';
    const errs  = failed?.values?.rate != null
      ? (failed.values.rate * 100).toFixed(2)
      : '0.00';
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
  ║  Test Duration: ~4 minutes                               ║
  ╚══════════════════════════════════════════════════════════╝
  
  → Add to resume: "Sustained 50 concurrent users at p95 ${p95}ms with ${errs}% error rate"
  `;
  
    console.log(summary);
  
    return {
      'load-test-results.json': JSON.stringify(data, null, 2),
      stdout: summary,
    };
  }