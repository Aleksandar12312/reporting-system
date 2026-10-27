import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiCall } from '../core/api-call';
import { AppMetaService } from '../core/app-meta.service';
import { DevModeService } from '../core/dev-mode.service';
import { ISSUE_COMPONENTS, IssueComponent, TIERS, Tier, UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { CallStatus } from '../shared/call-status';
import { chipLabel, hasProblem, partLabel, partsSeverity, problemsOf, sortByPartsFirst } from '../shared/issues';
import { TierBadge } from '../shared/tier-badge';

export type Mode = 'problematic' | 'worst' | 'scores';
export type SortKey =
  | 'unitId'
  | 'serialNumber'
  | 'modelName'
  | 'parts'
  | 'tier'
  | 'ratioToModel'
  | 'faults'
  | 'ratePerDay'
  | 'modelMedianRate'
  | 'exposureDays'
  | 'scope'
  | 'topPart';
type SortDir = 'asc' | 'desc';

const MODES: Mode[] = ['problematic', 'worst', 'scores'];
const SORT_KEYS: SortKey[] = [
  'unitId', 'serialNumber', 'modelName', 'parts', 'tier', 'ratioToModel', 'faults',
  'ratePerDay', 'modelMedianRate', 'exposureDays', 'scope', 'topPart',
];
const DEFAULT_LIMIT = 10;

/**
 * Unit ranking – GET /scores, /problematic and /worst (all return UnitScore[]).
 * Search, model/scope filters and sorting work on the loaded rows; the view lives in the URL.
 */
@Component({
  selector: 'app-scores-page',
  imports: [FormsModule, DecimalPipe, PercentPipe, TierBadge, CallStatus],
  templateUrl: './scores-page.html',
})
export class ScoresPage {
  private readonly api = inject(UnitsApiService);
  private readonly router = inject(Router);
  protected readonly meta = inject(AppMetaService);
  protected readonly dev = inject(DevModeService);

  protected readonly tiers = TIERS;
  protected readonly issueComponents = ISSUE_COMPONENTS;
  protected readonly problemsOf = problemsOf;
  protected readonly chipLabel = chipLabel;
  protected readonly partLabel = partLabel;

  // ---------- Request parameters ----------
  protected readonly mode = signal<Mode>('problematic');
  /** In "Problematic" it is sent to the backend; in the other modes it filters the loaded rows. */
  protected readonly tier = signal<'' | Tier>('');
  protected readonly limit = signal<number | null>(DEFAULT_LIMIT);

  // ---------- Client-side view ----------
  protected readonly search = signal('');
  protected readonly model = signal('');
  protected readonly scope = signal('');
  /** Only units whose scanner / recycler has a problem (ACTIVE or RECURRING). */
  protected readonly parts = signal<'' | IssueComponent>('');
  protected readonly sortKey = signal<SortKey | null>(null);
  protected readonly sortDir = signal<SortDir>('desc');

  protected readonly call = new ApiCall<UnitScore[]>();

  protected readonly tierOptions = computed<Tier[]>(() =>
    this.mode() === 'problematic' ? ['CRITICAL', 'WATCH'] : TIERS,
  );

  protected readonly modelOptions = computed(() => {
    const names = new Set((this.call.data() ?? []).map((u) => u.modelName).filter(Boolean));
    if (this.model()) names.add(this.model());
    return [...names].sort((a, b) => a.localeCompare(b));
  });

  protected readonly hasViewFilters = computed(
    () =>
      !!(
        this.search().trim() ||
        this.model() ||
        this.scope() ||
        this.parts() ||
        (this.mode() !== 'problematic' && this.tier())
      ),
  );

  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const model = this.model();
    const scope = this.scope();
    const parts = this.parts();
    const tier = this.mode() === 'problematic' ? '' : this.tier();
    const filtered = (this.call.data() ?? []).filter(
      (u) =>
        (!q || String(u.unitId) === q || (u.serialNumber ?? '').toLowerCase().includes(q)) &&
        (!model || u.modelName === model) &&
        (!scope || (scope === 'NONE' ? u.scope === null : u.scope === scope)) &&
        (!tier || u.tier === tier) &&
        (!parts || hasProblem(u, parts)),
    );
    const key = this.sortKey();
    // Default order: part problems first, then the backend's tier order.
    return key ? sortUnits(filtered, key, this.sortDir()) : sortByPartsFirst(filtered);
  });

  constructor() {
    const q = inject(ActivatedRoute).snapshot.queryParamMap;
    const mode = q.get('mode');
    if (MODES.includes(mode as Mode)) this.mode.set(mode as Mode);
    const tier = q.get('tier');
    if (TIERS.includes(tier as Tier)) this.tier.set(tier as Tier);
    const limit = Number(q.get('limit'));
    if (q.has('limit') && Number.isFinite(limit)) this.limit.set(limit);
    this.search.set(q.get('q') ?? '');
    this.model.set(q.get('model') ?? '');
    this.scope.set(q.get('scope') ?? '');
    const parts = q.get('parts');
    if (ISSUE_COMPONENTS.includes(parts as IssueComponent)) this.parts.set(parts as IssueComponent);
    const sort = q.get('sort');
    if (SORT_KEYS.includes(sort as SortKey)) this.sortKey.set(sort as SortKey);
    if (q.get('dir') === 'asc') this.sortDir.set('asc');
    if (this.mode() === 'problematic' && this.tier() && !this.tierOptions().includes(this.tier() as Tier)) {
      this.tier.set('');
    }

    this.load();

    // Keep the view in the URL so it can be bookmarked and shared.
    effect(() => {
      const params = {
        mode: this.mode() === 'problematic' ? null : this.mode(),
        tier: this.tier() || null,
        limit: this.mode() === 'worst' && this.limit() !== DEFAULT_LIMIT ? this.limit() : null,
        q: this.search().trim() || null,
        model: this.model() || null,
        scope: this.scope() || null,
        parts: this.parts() || null,
        sort: this.sortKey(),
        dir: this.sortKey() && this.sortDir() === 'asc' ? 'asc' : null,
      };
      this.router.navigate([], { queryParams: params, replaceUrl: true });
    });
  }

  protected setMode(mode: Mode): void {
    if (mode === this.mode()) return;
    this.mode.set(mode);
    if (mode === 'problematic' && this.tier() && !this.tierOptions().includes(this.tier() as Tier)) {
      this.tier.set('');
    }
    this.load();
  }

  protected onTierChange(value: '' | Tier): void {
    this.tier.set(value);
    if (this.mode() === 'problematic') this.load();
  }

  /** Dashboard card click: CRITICAL/WATCH open "Problematic", OK/NO_BASELINE filter "All units". */
  protected showTier(tier: Tier): void {
    const target: Mode = tier === 'CRITICAL' || tier === 'WATCH' ? 'problematic' : 'scores';
    const modeChanged = target !== this.mode();
    this.mode.set(target);
    this.tier.set(this.tier() === tier && !modeChanged ? '' : tier);
    if (modeChanged || target === 'problematic') this.load();
  }

  /** Dashboard "Scanner/Recycler problems" card: all units, filtered by that part. */
  protected showParts(component: IssueComponent | ''): void {
    const toggleOff = component !== '' && this.parts() === component && this.mode() === 'scores' && !this.tier();
    this.parts.set(toggleOff ? '' : component);
    if (component === '') return;
    this.tier.set('');
    if (this.mode() !== 'scores') {
      this.mode.set('scores');
      this.load();
    }
  }

  protected isActiveCard(tier: Tier): boolean {
    return this.tier() === tier;
  }

  protected load(): void {
    switch (this.mode()) {
      case 'scores':
        this.call.run(this.api.scores());
        break;
      case 'problematic':
        this.call.run(this.api.problematic((this.tier() || null) as Tier | null));
        break;
      case 'worst':
        this.call.run(this.api.worst(this.limit()));
        break;
    }
  }

  protected sortBy(key: SortKey): void {
    if (this.sortKey() !== key) {
      this.sortKey.set(key);
      this.sortDir.set(defaultDir(key));
    } else if (this.sortDir() === defaultDir(key)) {
      this.sortDir.set(defaultDir(key) === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(null); // third click: back to the default order (part problems, then tier)
    }
  }

  protected ariaSort(key: SortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey() !== key) return 'none';
    return this.sortDir() === 'asc' ? 'ascending' : 'descending';
  }

  protected sortMark(key: SortKey): string {
    if (this.sortKey() !== key) return '';
    return this.sortDir() === 'asc' ? '▲' : '▼';
  }

  protected clearViewFilters(): void {
    this.search.set('');
    this.model.set('');
    this.scope.set('');
    this.parts.set('');
    if (this.mode() !== 'problematic') this.tier.set('');
  }

  protected openUnit(unitId: number): void {
    this.router.navigate(['/units', unitId]);
  }
}

function defaultDir(key: SortKey): SortDir {
  return key === 'serialNumber' || key === 'modelName' || key === 'unitId' || key === 'tier' || key === 'scope' || key === 'topPart'
    ? 'asc'
    : 'desc';
}

/** Sorts a copy; null values always go last, ties keep unitId order. */
export function sortUnits(units: UnitScore[], key: SortKey, dir: SortDir): UnitScore[] {
  const sign = dir === 'asc' ? 1 : -1;
  const value = (u: UnitScore): string | number | null =>
    key === 'tier' ? TIERS.indexOf(u.tier) : key === 'parts' ? partsSeverity(u) : (u[key] as string | number | null);
  return [...units].sort((a, b) => {
    const va = value(a);
    const vb = value(b);
    if (va === null || va === undefined) return vb === null || vb === undefined ? a.unitId - b.unitId : 1;
    if (vb === null || vb === undefined) return -1;
    const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : String(va).localeCompare(String(vb));
    return cmp !== 0 ? cmp * sign : a.unitId - b.unitId;
  });
}
