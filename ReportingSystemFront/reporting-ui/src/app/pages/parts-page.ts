import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiCall } from '../core/api-call';
import { DevModeService } from '../core/dev-mode.service';
import { ISSUE_COMPONENTS, IssueComponent, UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { CallStatus } from '../shared/call-status';
import { isProblem, issueOf, partHint, partLabel, sortByPart, statusLabel } from '../shared/issues';
import { TierBadge } from '../shared/tier-badge';
import { UtcDatePipe } from '../shared/utc-date.pipe';

const DEFAULT_PART: IssueComponent = 'SCANNER';

/**
 * Part problems – which units have the worst scanner or recycler, from GET /api/units/scores.
 * Order: RECURRING, ACTIVE, OK, then the highest ratio to similar units. Tier is shown last, as context.
 */
@Component({
  selector: 'app-parts-page',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe, UtcDatePipe, TierBadge, CallStatus],
  templateUrl: './parts-page.html',
})
export class PartsPage {
  private readonly api = inject(UnitsApiService);
  private readonly router = inject(Router);
  protected readonly dev = inject(DevModeService);

  protected readonly components = ISSUE_COMPONENTS;
  protected readonly partLabel = partLabel;
  protected readonly partHint = partHint;
  protected readonly statusLabel = statusLabel;
  protected readonly issueOf = issueOf;

  protected readonly part = signal<IssueComponent>(DEFAULT_PART);
  /** Off: only units with an ACTIVE or RECURRING problem for the chosen part. */
  protected readonly showAll = signal(false);
  protected readonly search = signal('');
  protected readonly model = signal('');

  protected readonly call = new ApiCall<UnitScore[]>();

  /** Units with a problem, per part (for the tab counters). */
  protected readonly problemCounts = computed(() => {
    const units = this.call.data() ?? [];
    return Object.fromEntries(
      ISSUE_COMPONENTS.map((c) => [c, units.filter((u) => isProblem(issueOf(u, c))).length]),
    ) as Record<IssueComponent, number>;
  });

  protected readonly modelOptions = computed(() => {
    const names = new Set((this.call.data() ?? []).map((u) => u.modelName).filter(Boolean));
    if (this.model()) names.add(this.model());
    return [...names].sort((a, b) => a.localeCompare(b));
  });

  protected readonly rows = computed(() => {
    const part = this.part();
    const q = this.search().trim().toLowerCase();
    const model = this.model();
    const filtered = (this.call.data() ?? []).filter(
      (u) =>
        (this.showAll() || isProblem(issueOf(u, part))) &&
        (!q || String(u.unitId) === q || (u.serialNumber ?? '').toLowerCase().includes(q)) &&
        (!model || u.modelName === model),
    );
    return sortByPart(filtered, part);
  });

  protected readonly isScanner = computed(() => this.part() === 'SCANNER');

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    const part = (q.get('part') ?? '').toUpperCase();
    if (ISSUE_COMPONENTS.includes(part as IssueComponent)) this.part.set(part as IssueComponent);
    this.showAll.set(q.get('all') === '1');
    this.search.set(q.get('q') ?? '');
    this.model.set(q.get('model') ?? '');

    this.load();

    effect(() => {
      this.router.navigate([], {
        queryParams: {
          part: this.part() === DEFAULT_PART ? null : this.part().toLowerCase(),
          all: this.showAll() ? '1' : null,
          q: this.search().trim() || null,
          model: this.model() || null,
        },
        replaceUrl: true,
      });
    });
  }

  protected load(): void {
    this.call.run(this.api.scores());
  }

  protected openUnit(unitId: number): void {
    this.router.navigate(['/units', unitId]);
  }
}
