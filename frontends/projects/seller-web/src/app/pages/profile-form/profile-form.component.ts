import {Component, input, signal, inject, OnInit, DestroyRef} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {FormField, form} from '@angular/forms/signals';
import {MatButton} from '@angular/material/button';
import {MatFormField, MatLabel, MatError, MatHint} from '@angular/material/form-field';
import {MatInput} from '@angular/material/input';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatRadioButton, MatRadioGroup} from '@angular/material/radio';
import {SellerApiService} from '../../shared/service/seller-api.service';
import {SellerStatusService} from '../../shared/service/seller-status.service';
import {
  ProfileFormField, SubmitErrorView, buildSubmitRequest, emptyProfileForm, mapSubmitError, validateProfileForm,
  legalFieldsFor, fieldLabel, isFieldRequired, formValueFromProfile
} from './profile-form';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

/** `submit`: nộp hồ sơ mới (`/profile/new`); `resubmit`: sửa và nộp lại khi bị từ chối (`/profile/edit`, ô điền sẵn). */
export type ProfileFormMode = 'submit' | 'resubmit';

/**
 * Biểu mẫu hồ sơ người bán dùng chung cho nộp và nộp lại. Khung: mẫu HTML đã đủ ô và vị trí; phần logic (kiểm, gửi, lỗi)
 * còn các hàm tạm đánh dấu TODO, người viết nối vào hàm thuần ở bước 3.
 */
@Component({
  selector: 'app-profile-form',
  imports: [FormField, RouterLink, MatButton, MatFormField, MatLabel, MatError, MatHint, MatInput, MatRadioGroup, MatRadioButton],
  templateUrl: './profile-form.component.html',
  styleUrl: './profile-form.component.scss',
})
export class ProfileFormComponent implements OnInit {

  private readonly destroyRef = inject(DestroyRef);
  private readonly sellerApiService = inject(SellerApiService);
  private readonly sellerStatusService = inject(SellerStatusService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  private readonly _loadState = signal<'loading' | 'loaded' | 'error'>('loaded');

  readonly mode = input<ProfileFormMode>('submit');

  // Signal Forms (`@angular/forms/signals`, API công khai từ 22.0): mô hình là một signal ghi được, `form()` bọc quanh nó.
  // Mỗi ô là `form.<tên>`; giá trị đọc bằng `form.<tên>().value()`; gắn vào ô nhập bằng `[formField]="form.<tên>"`.
  private readonly _model = signal(emptyProfileForm());

  readonly form = form(this._model);

  // Cặp ghi/đọc: bên ngoài chỉ đọc được; muốn đổi phải gọi hàm.
  private readonly _submitting = signal(false);
  private readonly _formError = signal<string | null>(null);
  private readonly _rejectionReason = signal<string | null>(null);
  private readonly _submitAttempt = signal(false);
  /** Lỗi máy chủ gắn vào một ô, kèm giá trị của ô lúc bị từ chối. */
  private readonly _serverFieldError = signal<{field: ProfileFormField; message: string; value: string | null} | null>(null);
  readonly submitting = this._submitting.asReadonly();
  readonly formError = this._formError.asReadonly();
  readonly rejectionReason = this._rejectionReason.asReadonly();

  ngOnInit(): void {
    if (this.mode() === 'resubmit') this.loadProfile()
  }

  loadProfile() {
    this._loadState.set('loading');
    this.sellerApiService.getMyProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this._model.set(formValueFromProfile(profile));
          this._rejectionReason.set(profile.rejectionReason ?? null);
          this._loadState.set('loaded');
        },
        error: () => this._loadState.set('error'),
      })
  }

  // --- TODO (bước 5): các hàm tạm dưới đây chỉ để mẫu biên dịch được; thay bằng hàm thuần của bước 3. ---

  shows(_field: ProfileFormField): boolean {
    const legalFields: readonly ProfileFormField[] = legalFieldsFor(this.form.sellerType().value());
    return legalFields.includes(_field);
  }

  label(field: ProfileFormField): string {
    return fieldLabel(this.form.sellerType().value(), field);
  }

  required(_field: ProfileFormField): boolean {
    return isFieldRequired(this.form.sellerType().value(), _field);
  }

  /** Nhãn hiện trên ô: `label` kèm dấu * (bắt buộc) hoặc "(tùy chọn)". Giữ nguyên khi nối hàm thật ở `label` và `required`. */
  caption(field: ProfileFormField): string {
    return `${this.label(field)} ${this.required(field) ? '*' : '(tùy chọn)'}`;
  }

  /**
   * Thông báo lỗi của ô. Phần lỗi máy chủ đã nối: chỉ hiện khi ô còn đúng giá trị lúc bị từ chối, sửa là tự mất.
   * TODO: thêm lỗi kiểm phía giao diện (`validateProfileForm`, hiện sau lần bấm gửi đầu tiên).
   */
  errorOf(field: ProfileFormField): string | null {
    const server = this._serverFieldError();
    if (server?.field === field && server.value === this._model()[field]) return server.message;
    return null;
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    if (this._submitting()) return;
    this._submitAttempt(true)
    this._formError.set(null);
    this._serverFieldError.set(null);
    // Kiểm bằng hàm thuần, không dùng `form().valid()` (luôn true khi chưa đăng ký luật). Chưa hợp lệ thì thoát TRƯỚC khi khóa nút.
    if (Object.keys(validateProfileForm(this._model())).length > 0) return;
    this._submitting.set(true);

    const request = buildSubmitRequest(this._model());
    const call$ = this.mode() === 'submit'
      ? this.sellerApiService.submitSellerProfile(request)
      : this.sellerApiService.resubmitSellerProfile(request);

    call$.subscribe({
      next: () => {
        this.sellerStatusService.refresh().subscribe({
          complete: () => {
            void this.router
              .navigateByUrl('/profile')
              .finally(() => this._submitting.set(false))
          }
        })
      },
      error: (error: unknown) => this.showSubmitError(mapSubmitError(error))
    });
  }

  /**
   * Làm theo chỉ thị của `mapSubmitError`. Luồng lỗi không bao giờ `complete`, nên nút gửi phải được mở lại ở đây (trừ `stale`:
   * kết quả chưa rõ hoặc trạng thái đã đổi, giữ khóa tới khi chuyển trang để không gửi lại).
   */
  private showSubmitError(view: SubmitErrorView): void {
    if (view.kind !== 'stale') this._submitting.set(false);

    switch (view.kind) {
      case 'ignore':
        // 401: bộ chặn chung đã đưa về trang đăng nhập.
        return;
      case 'field':
        // Nhớ cả giá trị lúc bị từ chối: lỗi chỉ hiện khi ô còn đúng giá trị đó, sửa là tự mất.
        this._serverFieldError.set({field: view.field, message: view.message, value: this._model()[view.field]});
        return;
      case 'form':
      case 'retry':
        // `retry`: dữ liệu vẫn nằm trong `_model` nên người dùng chỉ việc bấm gửi lại.
        this._formError.set(view.message);
        return;
      case 'stale':
        // Form sắp biến mất nên không đặt `_formError`: báo bằng snackbar (sống qua lần chuyển trang), sau khi trạng thái đã được làm mới.
        this.sellerStatusService.refresh().subscribe(() => {
          this.snackBar.open(view.message, 'Đóng', {duration: 8000});
          void this.router.navigateByUrl('/').finally(() => this._submitting.set(false));
        });
        return;
    }
  }
}
