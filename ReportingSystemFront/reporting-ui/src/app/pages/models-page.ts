import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiCall } from '../core/api-call';
import { DevModeService } from '../core/dev-mode.service';
import { ModelStats } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { CallStatus } from '../shared/call-status';
import { UtcDatePipe } from '../shared/utc-date.pipe';

/** Models – GET /api/models (optional endpoint). A row opens the ranking filtered to that model. */
@Component({
  selector: 'app-models-page',
  imports: [DecimalPipe, DatePipe, UtcDatePipe, CallStatus],
  templateUrl: './models-page.html',
})
export class ModelsPage {
  private readonly api = inject(UnitsApiService);
  private readonly router = inject(Router);
  protected readonly dev = inject(DevModeService);

  protected readonly call = new ApiCall<ModelStats[]>();

  constructor() {
    this.load();
  }

  protected load(): void {
    this.call.run(this.api.models());
  }

  protected openModel(m: ModelStats): void {
    this.router.navigate(['/units'], { queryParams: { mode: 'scores', model: m.modelName } });
  }
}
