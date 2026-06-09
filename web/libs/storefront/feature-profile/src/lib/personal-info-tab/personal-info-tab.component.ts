import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService, IdentityService } from '@t3n/shared/data-access';

@Component({
  selector: 'app-personal-info-tab',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './personal-info-tab.component.html',
  styleUrl: './personal-info-tab.component.scss',
})
export class PersonalInfoTabComponent implements OnInit {
  private readonly fb              = inject(FormBuilder);
  private readonly identityService = inject(IdentityService);
  private readonly auth            = inject(AuthService);
  private readonly snackBar        = inject(MatSnackBar);

  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  protected readonly uploading = signal(false);
  protected readonly saving    = signal(false);
  protected readonly avatarUrl = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName:    ['', Validators.required],
    phoneNumber: ['', Validators.pattern('^0[0-9]{9}$')],
    email:       [{ value: '', disabled: true }],
  });

  ngOnInit(): void {
    this.identityService.getMe().subscribe(profile => {
      this.form.patchValue({
        fullName:    profile.fullName,
        phoneNumber: profile.phoneNumber ?? '',
        email:       profile.email,
      });
      this.avatarUrl.set(profile.avatarUrl);
      this.auth.patchUser({ fullName: profile.fullName, avatarUrl: profile.avatarUrl });
    });
  }

  triggerFileInput(): void {
    if (this.uploading()) return;
    this.fileInputRef.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    input.value = '';
    this.uploading.set(true);
    this.identityService.uploadAvatar(file).subscribe({
      next: res => {
        this.avatarUrl.set(res.avatarUrl);
        this.auth.patchUser({ avatarUrl: res.avatarUrl });
        this.uploading.set(false);
      },
      error: () => this.uploading.set(false),
    });
  }

  save(): void {
    if (this.form.invalid || this.form.pristine) return;
    this.saving.set(true);
    const { fullName, phoneNumber } = this.form.getRawValue();
    this.identityService.updateProfile({ fullName: fullName!, phoneNumber: phoneNumber || null }).subscribe({
      next: profile => {
        this.auth.patchUser({ fullName: profile.fullName });
        this.form.markAsPristine();
        this.saving.set(false);
        this.snackBar.open('Đã lưu thay đổi', undefined, { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('Lưu thất bại, vui lòng thử lại', undefined, { duration: 3000 });
      },
    });
  }
}
