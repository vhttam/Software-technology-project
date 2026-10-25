import '@testing-library/jest-dom';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { clearSession } from '../api/apiClient';
import { server } from './handlers';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'bypass' });
});

afterEach(() => {
  server.resetHandlers();
  clearSession();
  try {
    sessionStorage.clear();
  } catch {
    // ignore
  }
});

afterAll(() => {
  server.close();
});
