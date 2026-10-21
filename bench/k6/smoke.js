import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: 10, duration: '5s' };
export default function () {
  check(http.get('http://host.docker.internal:8099/ping'), { 'status 200': (r) => r.status === 200 });
}
