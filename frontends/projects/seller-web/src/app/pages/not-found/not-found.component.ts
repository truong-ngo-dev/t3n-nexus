import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';

/** Đích của mọi địa chỉ không có trong routes (path '**'). Chỉ có nội dung tĩnh và một lối về trang chủ. */
@Component({
  selector: 'app-not-found',
  imports: [RouterLink, MatButton],
  templateUrl: './not-found.component.html',
  styleUrl: './not-found.component.scss',
})
export class NotFoundComponent {}
