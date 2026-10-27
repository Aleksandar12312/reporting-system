import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MSG_MISSING_ENDPOINT, MSG_UNREACHABLE } from '../core/api-call';
import { FAULT_KINDS } from '../core/models';
import { HelpPage } from './help-page';

describe('HelpPage', () => {
  async function create() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(HelpPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('has a section for every entry of the contents', async () => {
    const el = await create();
    const links = [...el.querySelectorAll('.help-toc a')];
    expect(links.length).toBeGreaterThanOrEqual(10);
    for (const a of links) {
      const id = new URL((a as HTMLAnchorElement).href, 'http://x').hash.slice(1);
      expect(el.querySelector(`section#${id}`)).not.toBeNull();
    }
  });

  it('explains the exact error texts the app shows', async () => {
    const el = await create();
    const messages = [...el.querySelectorAll('.help-table.errors tbody td:first-child')].map((td) => td.textContent?.trim());
    expect(messages).toContain(MSG_UNREACHABLE);
    expect(messages).toContain(MSG_MISSING_ENDPOINT);
    expect(messages).toContain('Service is temporarily unavailable (503). Please try again later.');
    expect(messages).toContain('Unexpected error.');
  });

  it('lists the fault kinds and the scanner / recycler hints', async () => {
    const el = await create();
    expect(el.querySelectorAll('.fault-list li').length).toBe(FAULT_KINDS.length);
    expect(el.textContent).toContain('may need cleaning');
    expect(el.textContent).toContain('may need to be checked');
  });
});
