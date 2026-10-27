import { Component, inject, input } from '@angular/core';
import { ApiCall } from '../core/api-call';
import { DevModeService } from '../core/dev-mode.service';

/**
 * Shows "Loading…", an error box, or an empty-result note for one ApiCall.
 * In developer mode it also shows the URL that was called.
 */
@Component({
  selector: 'app-call-status',
  template: `
    @if (dev.enabled() && call().url(); as url) {
      <div class="request-url"><span class="method">{{ call().method() }}</span> {{ url }}</div>
    }
    @if (call().loading()) {
      <div class="status">Loading…</div>
    } @else if (call().error(); as error) {
      <div class="status error">{{ error }}</div>
    } @else if (isEmpty()) {
      <div class="status muted">{{ emptyText() }}</div>
    }
  `,
})
export class CallStatus {
  protected readonly dev = inject(DevModeService);

  readonly call = input.required<ApiCall<unknown>>();
  readonly emptyText = input('No results.');

  protected isEmpty(): boolean {
    const data = this.call().data();
    return Array.isArray(data) && data.length === 0;
  }
}
