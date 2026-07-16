import { describe, expect, it } from 'vitest';
import { toIndianE164 } from './auth';

describe('toIndianE164', () => {
  it('normalizes a ten digit Indian mobile number', () => {
    expect(toIndianE164('98765 43210')).toBe('+919876543210');
  });

  it('preserves an existing India country code', () => {
    expect(toIndianE164('+91 9876543210')).toBe('+919876543210');
  });
});
