import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ComponentIssue, FaultBreakdown, UnitEvent, UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { NONE, UnitDetailPage } from './unit-detail-page';

const events: UnitEvent[] = [
  { id: 3, unitId: 17, eventTime: '2026-10-26T12:00:00', type: 'DEVICE', part: 'RC', source: 'JAMMED' },
  { id: 2, unitId: 17, eventTime: '2026-10-26T11:00:00', type: 'DEVICE', part: null, source: 'SOFTWARE_ERROR' },
  { id: 1, unitId: 17, eventTime: '2026-10-26T10:00:00', type: 'STATUS', part: 'LOCK', source: 'DOOR_OPEN' },
];

describe('UnitDetailPage', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  function setup() {
    const req = <T>(url: string, value: T) => ({ url, result$: of(value) });
    api = {
      unit: vi.fn((id) => ({
        url: `/api/units/${id}`,
        result$: throwError(() => new HttpErrorResponse({ status: 404, error: { error: 'Unit not found' } })),
      })),
      scores: vi.fn(() => req('/api/units/scores', [])),
      unitFaults: vi.fn((id) => req(`/api/units/${id}/faults`, [] as FaultBreakdown[])),
      unitEvents: vi.fn((id) => req(`/api/units/${id}/events`, events)),
      unitFaultEvents: vi.fn((id) => req(`/api/units/${id}/fault-events`, events.slice(0, 2))),
      summary: vi.fn(() => req('/api/summary', null)),
      faultKinds: vi.fn(() => req('/api/fault-kinds', ['JAMMED', 'SOFTWARE_ERROR'])),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: UnitsApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(UnitDetailPage);
    fixture.componentRef.setInput('id', '17');
    return fixture;
  }

  it('loads everything for the unit and falls back to /scores when the unit endpoint answers 404', async () => {
    const fixture = setup();
    await fixture.whenStable();
    expect(api['unit']).toHaveBeenCalledWith('17');
    expect(api['scores']).toHaveBeenCalled();
    expect(api['unitFaults']).toHaveBeenCalledWith('17');
    expect(api['unitEvents']).toHaveBeenCalledWith('17', 100);
    expect(api['unitFaultEvents']).toHaveBeenCalledWith('17', 100);
  });

  it('reloads when the id changes', async () => {
    const fixture = setup();
    await fixture.whenStable();
    fixture.componentRef.setInput('id', '18');
    await fixture.whenStable();
    expect(api['unitFaults']).toHaveBeenCalledWith('18');
    expect(api['unitEvents']).toHaveBeenCalledWith('18', 100);
  });

  it('a breakdown row sets the component and source filters', async () => {
    const fixture = setup();
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      showFaultEventsFor(row: FaultBreakdown): void;
      partFilter(): string;
      sourceFilter(): string;
      tab(): string;
      filteredEvents(): UnitEvent[];
    };
    page.showFaultEventsFor({ unitId: 17, part: 'RC', fault: 'JAMMED', faults: 1, pctOfUnitFaults: 50 });
    expect(page.partFilter()).toBe('RC');
    expect(page.sourceFilter()).toBe('JAMMED');
    expect(page.tab()).toBe('fault-events');
    expect(page.filteredEvents().map((e) => e.id)).toEqual([3]);
  });

  it('maps the component UNKNOWN to "(none)"', async () => {
    const fixture = setup();
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as {
      showFaultEventsFor(row: FaultBreakdown): void;
      partFilter(): string;
      filteredEvents(): UnitEvent[];
    };
    page.showFaultEventsFor({ unitId: 17, part: 'UNKNOWN', fault: 'SOFTWARE_ERROR', faults: 1, pctOfUnitFaults: 50 });
    expect(page.partFilter()).toBe(NONE);
    expect(page.filteredEvents().map((e) => e.id)).toEqual([2]);
  });

  it('highlights fault sources from the backend list', async () => {
    const fixture = setup();
    await fixture.whenStable();
    const highlighted = [...(fixture.nativeElement as HTMLElement).querySelectorAll('#events .fault')].map((e) => e.textContent?.trim());
    expect(highlighted).toEqual(['JAMMED', 'SOFTWARE_ERROR']);
  });
});

describe('UnitDetailPage – scanner / recycler messages', () => {
  const issue = (partial: Partial<ComponentIssue>): ComponentIssue => ({
    component: 'SCANNER', status: 'OK', metric: 'REJECT_SHARE', value: 0.05, modelMedian: 0.08, ratio: 0.6, sample: 100,
    enoughData: true, countingSince: '2026-07-29T10:00:30', lastOkAt: null, lastOkBy: null, lastOkNote: null, ...partial,
  });
  const unit = (issues: ComponentIssue[]): UnitScore => ({
    unitId: 42, serialNumber: 'SN-42', modelName: 'Pro', faults: 10, exposureDays: 90, ratePerDay: 0.1, modelMedianRate: 0.1,
    ratioToModel: 1, tier: 'OK', topPart: null, topPartFaults: 0, topPartShare: null, scope: null, issues,
  });

  async function setup(u: UnitScore) {
    const req = <T>(url: string, value: T) => ({ url, result$: of(value) });
    const api = {
      unit: vi.fn(() => req('/api/units/42', u)),
      scores: vi.fn(() => req('/api/units/scores', [])),
      unitFaults: vi.fn(() => req('/f', [])),
      unitEvents: vi.fn(() => req('/e', [])),
      unitFaultEvents: vi.fn(() => req('/fe', [])),
      summary: vi.fn(() => req('/api/summary', null)),
      faultKinds: vi.fn(() => req('/api/fault-kinds', [])),
      ackHistory: vi.fn(() => req('/h', [])),
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: UnitsApiService, useValue: api }] });
    const fixture = TestBed.createComponent(UnitDetailPage);
    fixture.componentRef.setInput('id', '42');
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows one box per problem and "Mark all as OK" when both are wrong', async () => {
    const { el } = await setup(
      unit([
        issue({ component: 'RECYCLER', metric: 'FAULTS_PER_DAY', status: 'ACTIVE', value: 0.67, modelMedian: 0.15 }),
        issue({ component: 'SCANNER', status: 'ACTIVE', value: 0.3, modelMedian: 0.08 }),
      ]),
    );
    const titles = [...el.querySelectorAll('.issue-title')].map((e) => e.textContent?.trim());
    expect(titles).toEqual(['Something is wrong with the scanner.', 'Something is wrong with the recycler.']);
    expect(el.textContent).toContain('Rejects 30% of notes (similar units: 8%).');
    const hints = [...el.querySelectorAll('.issue-hint')].map((e) => e.textContent?.trim());
    expect(hints).toEqual(['The scanner may need cleaning.', 'The recycler may need to be checked.']);
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Mark all as OK')).toBe(true);
  });

  it('shows no box for status OK and no "Mark all" for a single problem', async () => {
    const { el } = await setup(unit([issue({ status: 'ACTIVE' }), issue({ component: 'RECYCLER', status: 'OK' })]));
    expect(el.querySelectorAll('.issue-box').length).toBe(1);
    expect(el.querySelector('.issue-box')?.classList).toContain('issue-ACTIVE');
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Mark all as OK')).toBe(false);
  });

  it('uses the RECURRING style', async () => {
    const { el } = await setup(unit([issue({ status: 'RECURRING', lastOkAt: '2026-10-20T08:15:02', lastOkBy: 'Marko' })]));
    expect(el.querySelector('.issue-box')?.classList).toContain('issue-RECURRING');
    expect(el.textContent).toContain('by Marko');
  });

  it('replaces the header with the POST answer, shows the confirmation and the quiet note', async () => {
    const { fixture, el } = await setup(unit([issue({ status: 'ACTIVE' })]));
    const page = fixture.componentInstance;
    page.openMarkOk('SCANNER');
    page.onMarkedOk(unit([issue({ status: 'OK', enoughData: false, lastOkAt: '2026-10-27T13:05:11', lastOkBy: 'Marko' })]));
    await fixture.whenStable();
    expect(el.querySelectorAll('.issue-box').length).toBe(0);
    expect(el.querySelector('.toast')?.textContent).toContain('Scanner marked as OK.');
    expect(el.querySelector('.collecting')?.textContent).toContain('Collecting new data since');
    expect(el.querySelector('app-mark-ok-dialog')).toBeNull();
  });
});
