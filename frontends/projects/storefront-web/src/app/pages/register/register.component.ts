import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { RegistrationService, registerErrorMessage } from '@t3n/auth';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const { password, confirm } = group.value as { password: string; confirm: string };
  return password === confirm ? null : { mismatch: true };
}

/**
 * Màn Đăng ký (H1). Kiểm ở giao diện chỉ để gợi ý; đúng sai do Xác thực quyết (T5). Ô nhập lại mật khẩu
 * chỉ để xác nhận, không gửi đi.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly registration = inject(RegistrationService);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.submitting()) return; // R7: bấm hai lần liên tiếp chỉ gửi một yêu cầu
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const { email, password } = this.form.getRawValue(); // `confirm` ở lại giao diện
    this.error.set(null);
    this.submitting.set(true);
    this.registration.register(email, password).subscribe((result) => {
      this.submitting.set(false);
      if (result.kind === 'created') this.done.set(true);
      else this.error.set(registerErrorMessage(result.failure));
    });
  }
}
