import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '@t3n/shared/data-access';

function confirmPasswordValidator(control: AbstractControl): ValidationErrors | null {
  const parent = control.parent;
  if (!parent) return null;
  return control.value === parent.get('password')?.value ? null : { mismatch: true };
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly fb     = inject(FormBuilder);
  private readonly auth   = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly submitting   = signal(false);
  protected readonly submitted    = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly showConfirm  = signal(false);

  protected readonly form = this.fb.group({
    fullName:        ['', Validators.required],
    email:           ['', [Validators.required, Validators.email]],
    password:        ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required, confirmPasswordValidator]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register({ fullName: fullName!, email: email!, password: password! }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.errorMessage.set('Email này đã được đăng ký. Vui lòng đăng nhập hoặc dùng email khác.');
        } else if (err.status === 429) {
          this.errorMessage.set('Bạn đã thử đăng ký quá nhiều lần. Vui lòng thử lại sau ít phút.');
        } else {
          this.errorMessage.set('Đăng ký thất bại. Vui lòng thử lại sau.');
        }
      },
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
