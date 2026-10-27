import { Injectable, computed, inject, signal } from '@angular/core';
import { DEFAULT_WINDOW_DAYS, FAULT_KINDS, Summary } from './models';
import { UnitsApiService } from './units-api.service';

/**
 * App-wide data from the optional endpoints GET /api/summary and GET /api/fault-kinds.
 * Both are optional: when the backend does not offer them yet, the app falls back to
 * the built-in fault-kind list and a 90-day window, and hides the dashboard and freshness line.
 */
@Injectable({ providedIn: 'root' })
export class AppMetaService {
  private readonly api = inject(UnitsApiService);

  readonly summary = signal<Summary | null>(null);
  readonly summaryLoading = signal(false);
  private readonly backendFaultKinds = signal<string[] | null>(null);

  /** Window length for texts such as "Faults (90 days)". */
  readonly windowDays = computed(() => this.summary()?.windowDays ?? DEFAULT_WINDOW_DAYS);

  /** Fault kinds from the backend, or the built-in list until that endpoint exists. */
  readonly faultKinds = computed<readonly string[]>(() => this.backendFaultKinds() ?? FAULT_KINDS);
  readonly faultKindSet = computed(() => new Set(this.faultKinds()));

  private faultKindsRequested = false;

  constructor() {
    this.loadSummary();
  }

  loadSummary(): void {
    this.summaryLoading.set(true);
    this.api.summary().result$.subscribe({
      next: (s) => {
        this.summary.set(s);
        this.summaryLoading.set(false);
      },
      error: () => {
        this.summary.set(null);
        this.summaryLoading.set(false);
      },
    });
  }

  /** Loads the fault kinds once; later calls do nothing. */
  ensureFaultKinds(): void {
    if (this.faultKindsRequested) return;
    this.faultKindsRequested = true;
    this.api.faultKinds().result$.subscribe({
      next: (kinds) => {
        if (Array.isArray(kinds) && kinds.length) this.backendFaultKinds.set(kinds);
      },
      error: () => {
        // Keep the built-in list; allow a retry later (e.g. after the backend restarts).
        this.faultKindsRequested = false;
      },
    });
  }

  isFault(source: string | null): boolean {
    return source !== null && this.faultKindSet().has(source);
  }
}
