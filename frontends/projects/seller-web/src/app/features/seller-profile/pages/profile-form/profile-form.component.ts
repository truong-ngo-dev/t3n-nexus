import {Component, ElementRef, input, signal, inject, OnInit, DestroyRef, computed} from '@angular/core';
import {Router, RouterLink} from '@angular/router';
import {FormField, form} from '@angular/forms/signals';
import {Observable} from 'rxjs';
import {MatButton} from '@angular/material/button';
import {MatFormField, MatLabel, MatError, MatHint} from '@angular/material/form-field';
import {MatInput} from '@angular/material/input';
import {ErrorStateMatcher} from '@angular/material/core';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatRadioButton, MatRadioGroup} from '@angular/material/radio';
import {SellerProfileApi} from '../../../../core/service/seller-profile/seller-profile.api';
import {SellerProfileCommandApi} from '../../data/seller-profile-command.api';
import {SellerStatusService} from '../../../../core/service/seller-profile/seller-status.service';
import {SellerType} from '../../../../core/service/seller-profile/seller-profile.model';
import {
  FORM_MODES, ProfileFormField, ProfileFormMode, ProfileFormValue, SubmitErrorView, buildShopInfoPatch, buildSubmitRequest,
  emptyProfileForm, fieldErrorMessage, fieldLabel, fieldsOf, formValueFromProfile, isDirty, isFieldRequired, legalFieldsFor,
  mapSubmitError, validateProfileForm,
} from '../../profile-form';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

/** Tên loại hình hiện ở khối "Đã khóa" khi sửa thông tin gian hàng. */
const TYPE_LABEL: Record<SellerType, string> = {
  INDIVIDUAL: 'Cá nhân',
  HOUSEHOLD: 'Hộ kinh doanh',
  BUSINESS: 'Doanh nghiệp',
};

/**
 * Biểu mẫu hồ sơ người bán cho bốn việc: nộp, sửa khi chờ duyệt, nộp lại, sửa thông tin gian hàng khi đã duyệt. `mode` lấy từ `data`
 * của route; mọi khác biệt (tiêu đề, nút, dòng ở đầu, có tải hồ sơ không, gọi API nào, rời về đâu) tra ở `FORM_MODES`.
 */
@Component({
  selector: 'app-profile-form',
  imports: [FormField, RouterLink, MatButton, MatFormField, MatLabel, MatError, MatHint, MatInput, MatRadioGroup, MatRadioButton],
  templateUrl: './profile-form.component.html',
  styleUrl: './profile-form.component.scss',
})
export class ProfileFormComponent implements OnInit {

  private readonly destroyRef = inject(DestroyRef);
  private readonly sellerProfileApi = inject(SellerProfileApi);
  private readonly commandApi = inject(SellerProfileCommandApi);
  private readonly sellerStatusService = inject(SellerStatusService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly mode = input<ProfileFormMode>('submit');
  /** Cột của `FORM_MODES` cho chế độ hiện tại; mẫu đọc tiêu đề, nút, dòng ở đầu từ đây. */
  readonly config = computed(() => FORM_MODES[this.mode()]);

  /** `loading` / `error` chỉ xảy ra ở chế độ có tải hồ sơ; nộp mới luôn `loaded`. */
  private readonly _loadState = signal<'loading' | 'loaded' | 'error'>('loaded');
  readonly loadState = this._loadState.asReadonly();

  // Signal Forms (`@angular/forms/signals`, API công khai từ 22.0): mô hình là một signal ghi được, `form()` bọc quanh nó.
  // Mỗi ô là `form.<tên>`; giá trị đọc bằng `form.<tên>().value()`; gắn vào ô nhập bằng `[formField]="form.<tên>"`.
  private readonly _model = signal(emptyProfileForm());
  readonly form = form(this._model);

  /** Giá trị lúc tải xong, để biết đã đổi gì (khóa nút "Lưu thay đổi", dựng thân PATCH). `null` khi nộp mới. */
  private readonly _original = signal<ProfileFormValue | null>(null);

  /** Kiểm bằng hàm thuần, chỉ các ô của chế độ hiện tại. `computed` chỉ tính lại khi `_model` hoặc `mode` đổi. */
  private readonly _errors = computed(() => validateProfileForm(this._model(), this.mode()));

  /** Đã đổi gì chưa; hai chế độ nộp luôn `true`. */
  readonly dirty = computed(() => isDirty(this.mode(), this._original(), this._model()));

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

  /** Nút gửi bật khi không đang gửi và (ở hai chế độ sửa) đã đổi ít nhất một ô. */
  readonly canSubmit = computed(() => !this._submitting() && this.dirty());

  /** Khối "Đã khóa" khi sửa thông tin gian hàng: tên gian hàng và loại hình, hiện bằng chữ (không phải ô nhập bị vô hiệu). */
  readonly lockedSummary = computed(() => {
    const original = this._original();
    if (!original) return null;
    return {shopName: original.shopName, type: original.sellerType ? TYPE_LABEL[original.sellerType] : ''};
  });

  ngOnInit(): void {
    if (this.config().loads) this.loadProfile();
  }

  /** Nạp hồ sơ để điền sẵn. Gọi lại được từ nút Thử lại. */
  loadProfile(): void {
    this._loadState.set('loading');
    this.sellerProfileApi.getMyProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          const value = formValueFromProfile(profile);
          this._model.set(value);
          this._original.set(value);
          this._rejectionReason.set(profile.rejectionReason ?? null);
          this._loadState.set('loaded');
        },
        error: () => this._loadState.set('error'),
      });
  }

  /** Ô có hiện thành ô nhập ở chế độ và loại hình hiện tại không. */
  shows(field: ProfileFormField): boolean {
    return fieldsOf(this.mode(), this.form.sellerType().value()).includes(field);
  }

  /** Nhóm pháp lý có hiện không: ba chế độ đầy đủ, và chỉ sau khi đã chọn loại hình. */
  showsLegal(): boolean {
    return this.mode() !== 'edit-approved' && legalFieldsFor(this.form.sellerType().value()).length > 0;
  }

  label(field: ProfileFormField): string {
    return fieldLabel(this.form.sellerType().value(), field);
  }

  required(field: ProfileFormField): boolean {
    return isFieldRequired(this.form.sellerType().value(), field);
  }

  /** Nhãn hiện trên ô: `label` kèm dấu * (bắt buộc) hoặc "(tùy chọn)". */
  caption(field: ProfileFormField): string {
    return `${this.label(field)} ${this.required(field) ? '*' : '(tùy chọn)'}`;
  }

  /**
   * Thông báo lỗi của ô, theo thứ tự ưu tiên:
   * 1. Lỗi máy chủ: chỉ hiện khi ô còn đúng giá trị lúc bị từ chối, sửa là tự mất.
   * 2. Lỗi kiểm phía giao diện ({@link validateProfileForm}): hiện sau lần bấm gửi đầu tiên, hoặc khi người dùng đã rời khỏi ô (`touched`).
   */
  errorOf(field: ProfileFormField): string | null {
    const server = this._serverFieldError();
    if (server?.field === field && server.value === this._model()[field]) return server.message;

    const error = this._errors()[field];
    if (!error || !(this._submitAttempt() || this.form[field]().touched())) return null;
    return fieldErrorMessage(this.form.sellerType().value(), field, error);
  }

  private readonly matchers = new Map<ProfileFormField, ErrorStateMatcher>();

  /**
   * Cho Material biết ô nào đang lỗi. Ta không đăng ký luật vào `form()` nên control luôn hợp lệ và `<mat-error>` sẽ không bao giờ hiện;
   * matcher này lấy `errorOf` làm nguồn duy nhất (viền đỏ và `<mat-error>` cùng theo đó). Mỗi ô một matcher, giữ lại để tham chiếu ổn định.
   */
  matcherOf(field: ProfileFormField): ErrorStateMatcher {
    let matcher = this.matchers.get(field);
    if (!matcher) {
      matcher = {isErrorState: () => this.errorOf(field) !== null};
      this.matchers.set(field, matcher);
    }
    return matcher;
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.canSubmit()) return;
    this._submitAttempt.set(true);
    this._formError.set(null);
    this._serverFieldError.set(null);
    // Kiểm bằng hàm thuần, không dùng `form().valid()` (luôn true khi chưa đăng ký luật). Chưa hợp lệ thì thoát TRƯỚC khi khóa nút.
    const errors = validateProfileForm(this._model(), this.mode());
    if (Object.keys(errors).length > 0) {
      this.focusFirstError(errors);
      return;
    }
    const call$ = this.submitCall();
    if (!call$) return;
    this._submitting.set(true);

    call$.subscribe({
      next: () => this.onSubmitted(),
      error: (error: unknown) => this.showSubmitError(mapSubmitError(error)),
    });
  }

  /** Lời gọi theo chế độ. `null` khi không có gì để gửi (sửa thông tin gian hàng mà không đổi ô nào). */
  private submitCall(): Observable<void> | null {
    const value = this._model();
    switch (this.mode()) {
      case 'submit': return this.commandApi.submit(buildSubmitRequest(value));
      case 'edit-pending': return this.commandApi.update(buildSubmitRequest(value));
      case 'resubmit': return this.commandApi.resubmit(buildSubmitRequest(value));
      case 'edit-approved': {
        const original = this._original();
        const patch = original ? buildShopInfoPatch(original, value) : null;
        return patch ? this.commandApi.updateShopInfo(patch) : null;
      }
    }
  }

  /**
   * Thành công: nộp và nộp lại đổi trạng thái hồ sơ nên làm mới trạng thái dùng chung TRƯỚC khi chuyển trang (guard của `/profile`);
   * hai chế độ sửa không đổi trạng thái, trang xem tự tải lại hồ sơ khi mở.
   */
  private onSubmitted(): void {
    const {refreshAfter, successMessage} = this.config();
    const leave = () => {
      this.snackBar.open(successMessage, undefined, {duration: 4000});
      void this.router.navigateByUrl('/profile').finally(() => this._submitting.set(false));
    };
    if (refreshAfter) this.sellerStatusService.refresh().subscribe({complete: leave});
    else leave();
  }

  /**
   * Đưa tiêu điểm tới ô lỗi đầu tiên theo thứ tự trên màn hình. Chờ một nhịp để mẫu vẽ xong lỗi vừa hiện; ô nhập tìm theo `data-testid`
   * trùng tên ô (với loại hình là nhóm radio nên tiêu điểm rơi vào nút radio đầu).
   */
  private focusFirstError(errors: ReturnType<typeof validateProfileForm>): void {
    const first = fieldsOf(this.mode(), this.form.sellerType().value()).find((field) => errors[field]);
    if (!first) return;
    queueMicrotask(() => {
      const host: HTMLElement | null = this.host.nativeElement.querySelector(`[data-testid="${first}"]`);
      const target = host?.matches('input, textarea') ? host : host?.querySelector<HTMLElement>('input, textarea');
      target?.focus();
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
        // Form sắp biến mất nên báo bằng snackbar (sống qua lần chuyển trang), sau khi trạng thái đã được làm mới.
        this.sellerStatusService.refresh().subscribe(() => {
          this.snackBar.open(view.message, 'Đóng', {duration: 8000});
          void this.router.navigateByUrl(this.config().leaveTo).finally(() => this._submitting.set(false));
        });
        return;
    }
  }
}
