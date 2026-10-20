import { RegisterFailure } from './registration.service';

const PASSWORD_RULE = 'Mật khẩu từ 8 đến 64 ký tự, có ít nhất một chữ cái và một chữ số, chỉ gồm A-Z, a-z, 0-9, @ và $.';

/** Bảng mã lỗi đăng ký thành thông báo (R6). Thành phần giao diện không tự so mã hay chữ. */
export function registerErrorMessage(failure: RegisterFailure): string {
  switch (failure.kind) {
    case 'email-taken':
      return 'Email đã được đăng ký';
    case 'email-unusable':
      return 'Email không dùng được để đăng ký';
    case 'password-invalid':
      return PASSWORD_RULE;
    case 'invalid':
      return 'Thông tin chưa hợp lệ, hãy kiểm tra lại các ô đã nhập.';
    case 'rate-limited':
      return failure.retryAfterSeconds === undefined
        ? 'Bạn thử quá nhiều lần, hãy chờ một lúc rồi thử lại.'
        : `Bạn thử quá nhiều lần, hãy thử lại sau ${failure.retryAfterSeconds} giây.`;
    case 'other':
      return 'Không đăng ký được, hãy thử lại sau.';
  }
}
