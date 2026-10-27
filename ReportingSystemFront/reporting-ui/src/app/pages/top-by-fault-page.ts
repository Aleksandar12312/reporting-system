import { DecimalPipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiCall } from '../core/api-call';
import { AppMetaService } from '../core/app-meta.service';
import { DevModeService } from '../core/dev-mode.service';
import { FaultRanking } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { CallStatus } from '../shared/call-status';

const DEFAULT_FAULT = 'JAMMED';
const DEFAULT_LIMIT = 10;

/** By fault kind – GET /api/units/top-by-fault?fault=&limit= (kinds from GET /api/fault-kinds). */
@Component({
  selector: 'app-top-by-fault-page',
  imports: [FormsModule, DecimalPipe, CallStatus],
  templateUrl: './top-by-fault-page.html',
})
export class TopByFaultPage {
  private readonly api = inject(UnitsApiService);
  private readonly router = inject(Router);
  protected readonly meta = inject(AppMetaService);
  protected readonly dev = inject(DevModeService);

  protected readonly fault = signal(DEFAULT_FAULT);
  protected readonly limit = signal<number | null>(DEFAULT_LIMIT);

  protected readonly call = new ApiCall<FaultRanking[]>();

  constructor() {
    this.meta.ensureFaultKinds();
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    if (q.get('fault')) this.fault.set(q.get('fault')!.toUpperCase());
    const limit = Number(q.get('limit'));
    if (q.has('limit') && Number.isFinite(limit)) this.limit.set(limit);
    this.load();

    effect(() => {
      this.router.navigate([], {
        queryParams: {
          fault: this.fault() === DEFAULT_FAULT ? null : this.fault(),
          limit: this.limit() === DEFAULT_LIMIT ? null : this.limit(),
        },
        replaceUrl: true,
      });
    });
  }

  protected onFaultChange(value: string): void {
    this.fault.set(value);
    this.load();
  }

  protected load(): void {
    this.call.run(this.api.topByFault(this.fault(), this.limit()));
  }

  protected openUnit(unitId: number): void {
    this.router.navigate(['/units', unitId]);
  }
}
