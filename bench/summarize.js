// Tóm tắt một bậc đo: đọc results/k6-last.json (k6) và results/stats.txt (docker stats), in một dòng.
// Dùng: node summarize.js <lần chạy> <số kết nối>
const fs = require('fs');
const [run, vus] = process.argv.slice(2);
const j = JSON.parse(fs.readFileSync('results/k6-last.json', 'utf8'));
const d = j.metrics.http_req_duration;
const failed = (j.metrics.http_req_failed || {}).value || 0;
const cpu = Object.fromEntries(fs.readFileSync('results/stats.txt', 'utf8').trim().split(/\r?\n/).map((l) => l.trim().split(/\s+/)));
console.log([run, vus, j.metrics.http_reqs.rate.toFixed(0), d.med.toFixed(2), d['p(95)'].toFixed(2), d['p(99)'].toFixed(2),
  (failed * 100).toFixed(2), cpu['bench-empty'], cpu['bench-k6']].join(' '));
