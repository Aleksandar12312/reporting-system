import { UtcDatePipe } from './utc-date.pipe';

describe('UtcDatePipe', () => {
  const pipe = new UtcDatePipe();

  it('treats a string without zone as UTC', () => {
    expect(pipe.transform('2026-10-26T12:00:48')?.toISOString()).toBe('2026-10-26T12:00:48.000Z');
  });

  it('keeps an explicit Z', () => {
    expect(pipe.transform('2026-10-26T12:00:48Z')?.toISOString()).toBe('2026-10-26T12:00:48.000Z');
  });

  it('respects an explicit offset', () => {
    expect(pipe.transform('2026-10-26T14:00:48+02:00')?.toISOString()).toBe('2026-10-26T12:00:48.000Z');
  });

  it('returns null for empty input', () => {
    expect(pipe.transform('')).toBeNull();
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeNull();
  });

  it('returns null for invalid input', () => {
    expect(pipe.transform('not a date')).toBeNull();
  });
});
