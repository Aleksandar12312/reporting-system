import { DatePipe, DecimalPipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiCall } from '../core/api-call';
import { AppMetaService } from '../core/app-meta.service';
import { DevModeService } from '../core/dev-mode.service';
import { FaultBreakdown, UnitEvent, UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { CallStatus } from '../shared/call-status';
import { TierBadge } from '../shared/tier-badge';
import { UtcDatePipe } from '../shared/utc-date.pipe';

type EventTab = 'events' | 'fault-events';

/** Select value meaning "the field is null / not recorded". */
export const NONE = '__none__';

/**
 * Unit window: header from GET /api/units/{id} (falls back to /scores while that endpoint
 * does not exist), breakdown from /{id}/faults, events from /{id}/events and /{id}/fault-events.
 */
@Component({
  selector: 'app-unit-detail-page',
  imports: [FormsModule, RouterLink, NgTemplateOutlet, DatePipe, DecimalPipe, PercentPipe, UtcDatePipe, TierBadge, CallStatus],
  templateUrl: './unit-detail-page.html',
})
export class UnitDetailPage {
  private readonly api = inject(UnitsApiService);
  protected readonly meta = inject(AppMetaService);
  protected readonly dev = inject(DevModeService);

  /** Route parameter :id (bound by withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly NONE = NONE;

  protected readonly tab = signal<EventTab>('events');
  protected readonly eventsLimit = signal<number | null>(100);
  protected readonly faultEventsLimit = signal<number | null>(100);

  protected readonly unit = new ApiCall<UnitScore>();
  /** Used only when GET /api/units/{id} answers 404. */
  protected readonly unitFallback = new ApiCall<UnitScore[]>();
  protected readonly faults = new ApiCall<FaultBreakdown[]>();
  protected readonly events = new ApiCall<UnitEvent[]>();
  protected readonly faultEvents = new ApiCall<UnitEvent[]>();

  // ---------- Filters (client-side, applied to the loaded event lists) ----------
  protected readonly search = signal('');
  protected readonly partFilter = signal('');
  protected readonly sourceFilter = signal('');
  protected readonly typeFilter = signal('');
  protected readonly breakdownSearch = signal('');

  protected readonly hasFilters = computed(
    () => !!(this.search().trim() || this.partFilter() || this.sourceFilter() || this.typeFilter()),
  );

  /** Events of the active tab (unfiltered). */
  private readonly activeList = computed(
    () => (this.tab() === 'events' ? this.events.data() : this.faultEvents.data()) ?? [],
  );

  /** Dropdown options are built from both lists, so switching tabs keeps the same choices. */
  private readonly allLoaded = computed(() => [...(this.events.data() ?? []), ...(this.faultEvents.data() ?? [])]);
  // The currently selected value is always kept as an option, so the select never shows blank.
  protected readonly partOptions = computed(() =>
    withSelected(distinct(this.allLoaded().map((e) => e.part)), this.partFilter()),
  );
  protected readonly sourceOptions = computed(() =>
    withSelected(distinct(this.allLoaded().map((e) => e.source)), this.sourceFilter()),
  );
  protected readonly typeOptions = computed(() =>
    withSelected(distinct(this.allLoaded().map((e) => e.type)), this.typeFilter()),
  );

  protected readonly filteredEvents = computed(() => {
    const q = this.search().trim().toLowerCase();
    const part = this.partFilter();
    const source = this.sourceFilter();
    const type = this.typeFilter();
    return this.activeList().filter(
      (e) =>
        matches(e.part, part) &&
        matches(e.source, source) &&
        matches(e.type, type) &&
        (!q || [e.id, e.part, e.source, e.type].some((v) => v !== null && String(v).toLowerCase().includes(q))),
    );
  });

  protected readonly activeTotal = computed(() => this.activeList().length);

  protected readonly filteredBreakdown = computed(() => {
    const q = this.breakdownSearch().trim().toLowerCase();
    const rows = this.faults.data() ?? [];
    return q ? rows.filter((f) => f.part.toLowerCase().includes(q) || f.fault.toLowerCase().includes(q)) : rows;
  });

  /** Header data: the unit endpoint, or the matching /scores row as a fallback. */
  protected readonly summary = computed<UnitScore | null>(() => {
    const direct = this.unit.data();
    if (direct) return direct;
    const id = Number(this.id());
    return this.unitFallback.data()?.find((u) => u.unitId === id) ?? null;
  });

  protected readonly headerLoading = computed(() => this.unit.loading() || this.unitFallback.loading());
  protected readonly headerError = computed(() => {
    if (this.unit.errorStatus() !== null && this.unit.errorStatus() !== 404) return this.unit.error();
    return this.unitFallback.error();
  });

  protected readonly maxFaults = computed(() =>
    Math.max(1, ...(this.faults.data() ?? []).map((f) => f.faults)),
  );

  constructor() {
    this.meta.ensureFaultKinds();
    // Reload everything (and reset filters) whenever the :id in the URL changes.
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.clearFilters();
        this.breakdownSearch.set('');
        this.loadAll(id);
      });
    });
  }

  protected loadAll(id = this.id()): void {
    this.loadHeader(id);
    this.loadFaults(id);
    this.loadEvents(id);
    this.loadFaultEvents(id);
  }

  protected loadHeader(id = this.id()): void {
    this.unitFallback.data.set(null);
    this.unitFallback.error.set(null);
    this.unit.run(this.api.unit(id), () => {
      // 404: either the endpoint does not exist yet or the unit is unknown – /scores answers both.
      if (this.unit.errorStatus() === 404) {
        this.unitFallback.run(this.api.scores());
      }
    });
  }

  protected loadFaults(id = this.id()): void {
    this.faults.run(this.api.unitFaults(id));
  }

  protected loadEvents(id = this.id()): void {
    this.events.run(this.api.unitEvents(id, this.eventsLimit()));
  }

  protected loadFaultEvents(id = this.id()): void {
    this.faultEvents.run(this.api.unitFaultEvents(id, this.faultEventsLimit()));
  }

  protected clearFilters(): void {
    this.search.set('');
    this.partFilter.set('');
    this.sourceFilter.set('');
    this.typeFilter.set('');
  }

  /** Clicking a breakdown row shows the matching fault events below. */
  showFaultEventsFor(row: FaultBreakdown): void {
    this.clearFilters();
    // The breakdown reports a missing component as "UNKNOWN"; events carry null instead.
    const knownParts = distinct(this.allLoaded().map((e) => e.part));
    this.partFilter.set(row.part === 'UNKNOWN' && !knownParts.includes('UNKNOWN') ? NONE : row.part);
    this.sourceFilter.set(row.fault);
    this.tab.set('fault-events');
    document.getElementById('events')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }

  protected isFault(source: string | null): boolean {
    return this.meta.isFault(source);
  }
}

function distinct(values: (string | null)[]): string[] {
  const set = new Set<string>();
  let hasNull = false;
  for (const v of values) {
    if (v === null || v === '') hasNull = true;
    else set.add(v);
  }
  const sorted = [...set].sort((a, b) => a.localeCompare(b));
  return hasNull ? [...sorted, NONE] : sorted;
}

function withSelected(options: string[], selected: string): string[] {
  return !selected || options.includes(selected) ? options : [...options, selected];
}

function matches(value: string | null, filter: string): boolean {
  if (!filter) return true;
  if (filter === NONE) return value === null || value === '';
  return value === filter;
}
