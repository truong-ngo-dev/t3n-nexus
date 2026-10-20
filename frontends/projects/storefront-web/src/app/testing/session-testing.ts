import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SESSION_URL, SessionService } from '@t3n/auth';

type Outcome = { status: 200; body: unknown } | { status: 401 } | { status: 503 };

/** Đưa dịch vụ phiên về trạng thái mong muốn bằng một lần thăm dò có phản hồi giả (thay cho bộ khởi động). */
export function probeWith(outcome: Outcome): void {
  TestBed.inject(SessionService).probe().subscribe();
  const req = TestBed.inject(HttpTestingController).expectOne(SESSION_URL);
  if (outcome.status === 200) req.flush(outcome.body);
  else req.flush(null, { status: outcome.status, statusText: String(outcome.status) });
}

export const asUser = (role = 'USER'): Outcome => ({ status: 200, body: { userId: 'u-123', role } });
