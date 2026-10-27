import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FaultBreakdown, UnitEvent } from '../core/models';
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
