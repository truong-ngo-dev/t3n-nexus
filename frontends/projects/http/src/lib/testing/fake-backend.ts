import { HttpBackend, HttpErrorResponse, HttpEvent, HttpEventType, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Một câu trả lời của máy chủ giả: thành công (200) hoặc lỗi kèm mã trạng thái, nội dung, tiêu đề. */
export type Reply = (req: HttpRequest<unknown>) => Observable<HttpEvent<unknown>>;

/** Giống `HttpXhrBackend`: phát sự kiện `Sent` trước rồi mới tới phản hồi (nguồn gốc của R4). */
export function ok(body: unknown = null, onSend?: () => void): Reply {
  return (req) =>
    new Observable((observer) => {
      onSend?.();
      observer.next({ type: HttpEventType.Sent });
      observer.next(new HttpResponse({ status: 200, body, url: req.url }));
      observer.complete();
    });
}

/** 202 kèm `Location`, như `POST /bff/auth/logout`. */
export function accepted(location: string): Reply {
  return (req) =>
    new Observable((observer) => {
      observer.next({ type: HttpEventType.Sent });
      observer.next(new HttpResponse({ status: 202, headers: new HttpHeaders({ Location: location }), url: req.url }));
      observer.complete();
    });
}

export function fail(status: number, error: unknown = null, headers: Record<string, string> = {}): Reply {
  return (req) =>
    new Observable((observer) => {
      observer.next({ type: HttpEventType.Sent });
      observer.error(new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers), url: req.url }));
    });
}

/** Máy chủ giả theo kịch bản: lần gọi thứ n dùng câu trả lời thứ n, hết kịch bản thì dùng câu cuối. Ghi lại mọi yêu cầu. */
export class FakeBackend implements HttpBackend {
  readonly requests: HttpRequest<unknown>[] = [];

  constructor(private readonly script: Reply[]) {}

  handle(req: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    const reply = this.script[Math.min(this.requests.length, this.script.length - 1)];
    this.requests.push(req);
    return reply(req);
  }

  /** Dòng "PHƯƠNG_THỨC url" của từng yêu cầu đã tới máy chủ, để so thứ tự. */
  get calls(): string[] {
    return this.requests.map((r) => `${r.method} ${r.url}`);
  }
}
