import { Component, ElementRef, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiCall } from '../core/api-call';
import { DevModeService } from '../core/dev-mode.service';
import { UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { AckTarget, partName } from './issues';

export const NAME_STORAGE_KEY = 'reporting-ui.ackName';
export const NAME_MAX = 100;
export const NOTE_MAX = 500;

/**
 * "Mark as OK" dialog: POST /api/units/{id}/issues/{component}/ack, or /issues/ack for all.
 * Emits the updated UnitScore on success; on error it stays open and shows the message.
 */
@Component({
  selector: 'app-mark-ok-dialog',
  imports: [FormsModule],
  template: `
    <div class="dialog-backdrop" (click)="cancel()"></div>
    <div
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mark-ok-title"
      (keydown.escape)="cancel()"
    >
      <h2 id="mark-ok-title">{{ title() }}</h2>
      <p class="muted small">Unit #{{ unitId() }}. The backend starts counting this part again from now.</p>

      <form (ngSubmit)="submit()">
        <label class="field">
          <span>Name</span>
          <input
            #nameInput
            name="by"
            type="text"
            required
            autocomplete="name"
            [maxlength]="nameMax"
            [(ngModel)]="name"
          />
        </label>
        <label class="field">
          <span>Note <span class="muted">(optional)</span></span>
          <textarea
            name="note"
            rows="3"
            [maxlength]="noteMax"
            [placeholder]="'e.g. ' + (target() === 'RECYCLER' ? 'jam cleared' : 'scanner cleaned')"
            [(ngModel)]="note"
          ></textarea>
          <span class="muted small counter">{{ note().length }} / {{ noteMax }}</span>
        </label>

        @if (dev.enabled() && call.url(); as url) {
          <div class="request-url"><span class="method">POST</span> {{ url }}</div>
        }
        @if (call.error(); as error) {
          <div class="status error" role="alert">{{ error }}</div>
        }

        <div class="dialog-actions">
          <button type="button" (click)="cancel()">Cancel</button>
          <button type="submit" class="primary" [disabled]="!canSubmit()">
            {{ call.loading() ? 'Saving…' : 'Mark as OK' }}
          </button>
        </div>
      </form>
    </div>
  `,
})
export class MarkOkDialog {
  private readonly api = inject(UnitsApiService);
  protected readonly dev = inject(DevModeService);

  readonly unitId = input.required<number | string>();
  readonly target = input.required<AckTarget>();

  /** The unit as the backend returned it after the POST. */
  readonly saved = output<UnitScore>();
  readonly closed = output<void>();

  protected readonly nameMax = NAME_MAX;
  protected readonly noteMax = NOTE_MAX;

  protected readonly name = signal(readStoredName());
  protected readonly note = signal('');
  protected readonly call = new ApiCall<UnitScore>();

  protected readonly title = computed(() =>
    this.target() === 'ALL' ? 'Mark all problems as OK' : `Mark ${partName(this.target() as 'SCANNER')} as OK`,
  );

  protected readonly canSubmit = computed(() => this.name().trim().length > 0 && !this.call.loading());

  private readonly nameInput = viewChild<ElementRef<HTMLInputElement>>('nameInput');

  constructor() {
    afterNextRender(() => {
      const el = this.nameInput()?.nativeElement;
      if (!el) return;
      el.focus();
      if (el.value) el.select();
    });
  }

  submit(): void {
    if (!this.canSubmit()) return;
    const by = this.name().trim().slice(0, NAME_MAX);
    const note = this.note().trim().slice(0, NOTE_MAX) || null;
    const target = this.target();
    const request =
      target === 'ALL'
        ? this.api.markAllOk(this.unitId(), { by, note })
        : this.api.markOk(this.unitId(), target, { by, note });
    storeName(by);
    this.call.run(request, { onSuccess: (unit) => this.saved.emit(unit) });
  }

  protected cancel(): void {
    if (this.call.loading()) return;
    this.closed.emit();
  }
}

function readStoredName(): string {
  try {
    return localStorage.getItem(NAME_STORAGE_KEY) ?? '';
  } catch {
    return '';
  }
}

function storeName(name: string): void {
  try {
    localStorage.setItem(NAME_STORAGE_KEY, name);
  } catch {
    // storage blocked – the name just won't be pre-filled next time
  }
}
