import { Injectable } from '@angular/core';
import { from, Observable, shareReplay } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DeviceFingerprintService {
  private readonly hash$: Observable<string> = from(this.compute()).pipe(shareReplay(1));

  getHash(): Observable<string> {
    return this.hash$;
  }

  private async compute(): Promise<string> {
    try {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d')!;
      ctx.textBaseline = 'top';
      ctx.font = '14px Arial';
      ctx.fillStyle = '#f60';
      ctx.fillRect(125, 1, 62, 20);
      ctx.fillStyle = '#069';
      ctx.fillText('T3Nexus fp', 2, 15);
      ctx.fillStyle = 'rgba(102,204,0,0.7)';
      ctx.fillText('T3Nexus fp', 4, 17);

      const signals = [
        canvas.toDataURL(),
        screen.width + 'x' + screen.height,
        screen.colorDepth,
        Intl.DateTimeFormat().resolvedOptions().timeZone,
        navigator.hardwareConcurrency,
        navigator.language,
      ].join('|');

      const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(signals));
      return Array.from(new Uint8Array(buf))
        .map(b => b.toString(16).padStart(2, '0'))
        .join('');
    } catch {
      return '';
    }
  }
}
