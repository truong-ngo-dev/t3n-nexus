import http from 'k6/http';
import { check } from 'k6';

// Điểm cuối rỗng: VUS kết nối đồng thời, không nghỉ giữa các yêu cầu, DUR thời lượng.
export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DUR || '30s',
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const r = http.get(`http://${__ENV.TARGET || 'bench-empty:8099'}/ping`);
  check(r, { 'status 200': (x) => x.status === 200 });
}
