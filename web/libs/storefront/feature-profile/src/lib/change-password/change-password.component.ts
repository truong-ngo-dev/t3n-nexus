import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { IdentityService } from '@t3n/shared/data-access';

function confirmPasswordValidator(control: AbstractControl): ValidationErrors | null {
  const parent = control.parent;
  if (!parent) return null;
  return control.value === parent.get('newPassword')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent implements OnInit {
  private readonly fb              = inject(FormBuilder);
  private readonly identityService = inject(IdentityService);
  private readonly snackBar        = inject(MatSnackBar);

  protected readonly loading        = signal(true);
  protected readonly saving         = signal(false);
  protected readonly resending      = signal(false);
  protected readonly hasPassword    = signal<boolean | null>(null);
  protected readonly resendCooldown = signal(false);
  protected readonly showCurrent    = signal(false);
  protected readonly showNew        = signal(false);
  protected readonly showConfirm    = signal(false);

  protected readonly form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword:     ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required, confirmPasswordValidator]],
  });

  ngOnInit(): void {
    this.identityService.getPasswordStatus().subscribe({
      next: res => {
        this.hasPassword.set(res.hasPassword);
        this.loading.set(false);
      },
      error: () => {
        this.hasPassword.set(true);
        this.loading.set(false);
      },
    });
  }

  resendSetupEmail(): void {
    if (this.resending() || this.resendCooldown()) return;
    this.resending.set(true);
    this.identityService.requestPasswordSetup().subscribe({
      next: () => {
        this.resending.set(false);
        this.resendCooldown.set(true);
        this.snackBar.open('Đã gửi lại email thiết lập mật khẩu', undefined, { duration: 4000 });
        setTimeout(() => this.resendCooldown.set(false), 60_000);
      },
      error: () => {
        this.resending.set(false);
        this.snackBar.open('Gửi thất bại, vui lòng thử lại sau', undefined, { duration: 3000 });
      },
    });
  }

  save(): void {
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.identityService.changePassword({
      currentPassword: currentPassword!,
      newPassword: newPassword!,
    }).subscribe({
      next: () => {
        this.form.reset();
        this.saving.set(false);
        this.snackBar.open('Đã đổi mật khẩu thành công', undefined, { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Mật khẩu hiện tại không đúng', undefined, { duration: 3000 });
      },
    });
  }
}
