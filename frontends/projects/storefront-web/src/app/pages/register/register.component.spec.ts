import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AUTH_BASE_URL } from '@t3n/auth';
import { RegisterComponent } from './register.component';

const URL = 'http://auth.test/register';

describe('RegisterComponent (KT-07)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AUTH_BASE_URL, useValue: 'http://auth.test' }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const q = <T extends HTMLElement>(id: string) => el.querySelector(`[data-testid="${id}"]`) as T | null;
    const type = (id: string, value: string) => {
      const input = q<HTMLInputElement>(id)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const fill = (email = 'a@b.vn', password = 'Passw0rd', confirm = password) => {
      type('email', email);
      type('password', password);
      type('confirm', confirm);
      fixture.detectChanges();
    };
    const submit = () => {
      q<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { fixture, el, q, fill, submit };
  }

  it('form rỗng hoặc sai: không gửi gì, hiện gợi ý', () => {
    const { submit, el } = render();
    submit();

    http.expectNone(URL);
    expect(el.querySelector('mat-error')).not.toBeNull();
  });

  it('email sai định dạng: không gửi', () => {
    const { fill, submit } = render();
    fill('khong-phai-email', 'Passw0rd');
    submit();

    http.expectNone(URL);
  });

  it('nhập lại mật khẩu không khớp: không gửi', () => {
    const { fill, submit } = render();
    fill('a@b.vn', 'Passw0rd', 'Khac1234');
    submit();

    http.expectNone(URL);
  });

  it('hợp lệ: gửi đúng một lần chỉ {email, password}, ô nhập lại không đi theo', () => {
    const { fill, submit } = render();
    fill('a@b.vn', 'Passw0rd');
    submit();

    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ email: 'a@b.vn', password: 'Passw0rd' });
    req.flush({}, { status: 201, statusText: 'Created' });
  });

  // R7
  it('đang gửi: nút bị khóa; bấm hai lần liên tiếp chỉ gửi một yêu cầu', () => {
    const { fill, submit, q, fixture } = render();
    fill();
    submit();
    submit();

    expect(q<HTMLButtonElement>('submit')!.disabled).toBe(true);
    const req = http.expectOne(URL); // expectOne ném lỗi nếu có hai yêu cầu
    req.flush({}, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
  });

  it('201: thông báo thành công và đường tới Đăng nhập', () => {
    const { fill, submit, q, fixture, el } = render();
    fill();
    submit();
    http.expectOne(URL).flush({}, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(q('success')?.textContent).toContain('thành công');
    expect(el.querySelector('a[href="/login"]')).not.toBeNull();
    expect(q('form')).toBeNull();
  });

  it('409: hiện "Email đã được đăng ký", mở khóa nút và giữ nội dung đã nhập', () => {
    const { fill, submit, q, fixture } = render();
    fill('a@b.vn', 'Passw0rd');
    submit();
    http.expectOne(URL).flush({ success: false, code: 'EMAIL_TAKEN', message: 'x' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(q('error')?.textContent).toContain('Email đã được đăng ký');
    expect(q<HTMLButtonElement>('submit')!.disabled).toBe(false);
    expect(q<HTMLInputElement>('email')!.value).toBe('a@b.vn');
  });

  it('400 PASSWORD_INVALID: nêu quy tắc mật khẩu', () => {
    const { fill, submit, q, fixture } = render();
    fill();
    submit();
    http.expectOne(URL).flush({ success: false, code: 'PASSWORD_INVALID', message: 'x' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(q('error')?.textContent).toMatch(/8 đến 64/);
  });

  it('429: nêu số giây chờ', () => {
    const { fill, submit, q, fixture } = render();
    fill();
    submit();
    http.expectOne(URL).flush({ code: 'TOO_MANY_REQUESTS' }, { status: 429, statusText: 'x', headers: { 'Retry-After': '30' } });
    fixture.detectChanges();

    expect(q('error')?.textContent).toContain('30');
  });

  it('lỗi mạng: thử lại sau, cho bấm lại và gửi lần nữa được', () => {
    const { fill, submit, q, fixture } = render();
    fill();
    submit();
    http.expectOne(URL).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();
    expect(q('error')?.textContent).toMatch(/thử lại sau/i);

    submit();
    http.expectOne(URL).flush({}, { status: 201, statusText: 'Created' });
  });

  it('lần gửi mới xóa thông báo lỗi cũ', () => {
    const { fill, submit, q, fixture } = render();
    fill();
    submit();
    http.expectOne(URL).flush({ code: 'EMAIL_TAKEN' }, { status: 409, statusText: 'x' });
    fixture.detectChanges();
    expect(q('error')).not.toBeNull();

    submit();
    expect(q('error')).toBeNull();
    http.expectOne(URL).flush({}, { status: 201, statusText: 'Created' });
  });
});
