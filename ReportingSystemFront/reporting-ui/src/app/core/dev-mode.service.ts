import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'reporting-ui.devMode';

/**
 * Developer mode shows endpoint names in tab labels, the "GET /api/..." line above each
 * result and "Call" buttons. Off by default; remembered per browser.
 */
@Injectable({ providedIn: 'root' })
export class DevModeService {
  readonly enabled = signal(readStored());

  toggle(): void {
    this.set(!this.enabled());
  }

  set(value: boolean): void {
    this.enabled.set(value);
    try {
      localStorage.setItem(STORAGE_KEY, value ? '1' : '0');
    } catch {
      // storage blocked – keep the in-memory value
    }
  }
}

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}
