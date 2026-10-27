import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { MarkOkDialog, NAME_STORAGE_KEY } from './mark-ok-dialog';

describe('MarkOkDialog', () => {
  let api: { markOk: ReturnType<typeof vi.fn>; markAllOk: ReturnType<typeof vi.fn> };
  let answer: Subject<UnitScore>;

  async function setup(target: 'SCANNER' | 'RECYCLER' | 'ALL') {
    answer = new Subject<UnitScore>();
    api = {
      markOk: vi.fn((id, c) => ({ url: `/api/units/${id}/issues/${c}/ack`, method: 'POST', result$: answer })),
      markAllOk: vi.fn((id) => ({ url: `/api/units/${id}/issues/ack`, method: 'POST', result$: answer })),
    };
    TestBed.configureTestingModule({ providers: [{ provide: UnitsApiService, useValue: api }] });
    const fixture = TestBed.createComponent(MarkOkDialog);
    fixture.componentRef.setInput('unitId', 42);
    fixture.componentRef.setInput('target', target);
    await fixture.whenStable();
    return fixture;
  }

  const el = (f: { nativeElement: HTMLElement }) => f.nativeElement;
  const submitButton = (f: { nativeElement: HTMLElement }) =>
    el(f).querySelector<HTMLButtonElement>('button[type=submit]')!;

  async function type(fixture: Awaited<ReturnType<typeof setup>>, selector: string, value: string) {
    const input = el(fixture).querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  beforeEach(() => {
    try {
      localStorage.removeItem(NAME_STORAGE_KEY);
    } catch {
      /* ignore */
    }
  });

  it('cannot be submitted without a name', async () => {
    const fixture = await setup('SCANNER');
    expect(submitButton(fixture).disabled).toBe(true);
    await type(fixture, 'input[name=by]', '   ');
    expect(submitButton(fixture).disabled).toBe(true);
    await type(fixture, 'input[name=by]', 'Marko');
    expect(submitButton(fixture).disabled).toBe(false);
  });

  it('posts name and note for one component and emits the returned unit', async () => {
    const fixture = await setup('SCANNER');
    const saved = vi.fn();
    fixture.componentInstance.saved.subscribe(saved);
    await type(fixture, 'input[name=by]', ' Marko ');
    await type(fixture, 'textarea[name=note]', 'scanner cleaned');
    fixture.componentInstance.submit();
    expect(api.markOk).toHaveBeenCalledWith(42, 'SCANNER', { by: 'Marko', note: 'scanner cleaned' });
    await fixture.whenStable();
    expect(submitButton(fixture).disabled).toBe(true); // while the POST runs
    const unit = { unitId: 42 } as UnitScore;
    answer.next(unit);
    expect(saved).toHaveBeenCalledWith(unit);
  });

  it('uses the "all" endpoint and sends null for an empty note', async () => {
    const fixture = await setup('ALL');
    await type(fixture, 'input[name=by]', 'Ana');
    fixture.componentInstance.submit();
    expect(api.markAllOk).toHaveBeenCalledWith(42, { by: 'Ana', note: null });
  });

  it('remembers the name for the next dialog', async () => {
    const first = await setup('SCANNER');
    await type(first, 'input[name=by]', 'Marko');
    first.componentInstance.submit();
    TestBed.resetTestingModule();
    const second = await setup('RECYCLER');
    expect(el(second).querySelector<HTMLInputElement>('input[name=by]')!.value).toBe('Marko');
  });

  it('stays open and shows the backend error inside the dialog', async () => {
    const fixture = await setup('SCANNER');
    api.markOk.mockReturnValue({
      url: '/api/units/42/issues/SCANNER/ack',
      method: 'POST',
      result$: throwError(() => new HttpErrorResponse({ status: 404, error: { error: 'Unit 42 not found' } })),
    });
    const saved = vi.fn();
    fixture.componentInstance.saved.subscribe(saved);
    await type(fixture, 'input[name=by]', 'Marko');
    fixture.componentInstance.submit();
    await fixture.whenStable();
    expect(saved).not.toHaveBeenCalled();
    expect(el(fixture).querySelector('.status.error')?.textContent).toContain('Unit 42 not found');
    expect(submitButton(fixture).disabled).toBe(false);
  });
});
