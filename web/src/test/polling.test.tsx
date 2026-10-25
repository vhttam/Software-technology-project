import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { usePolling } from '../hooks/usePolling';

interface TestJob {
  id: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED';
}

const PollingTester: React.FC<{
  fetcher: () => Promise<TestJob>;
  intervalMs?: number;
  maxDurationMs?: number;
}> = ({ fetcher, intervalMs = 100, maxDurationMs = 1000 }) => {
  const { data, isCompleted, isTimeout } = usePolling<TestJob>({
    fn: fetcher,
    isTerminal: (job) => job.status === 'COMPLETED' || job.status === 'CANCELLED',
    intervalMs,
    maxDurationMs,
  });

  return (
    <div>
      <div data-testid="status">{data?.status || 'NONE'}</div>
      <div data-testid="completed">{isCompleted ? 'YES' : 'NO'}</div>
      <div data-testid="timeout">{isTimeout ? 'YES' : 'NO'}</div>
    </div>
  );
};

describe('usePolling hook', () => {
  it('polls repeatedly while in non-terminal state and stops immediately when terminal state is reached', async () => {
    vi.useFakeTimers();

    const statuses: Array<'PENDING' | 'PROCESSING' | 'COMPLETED'> = [
      'PENDING',
      'PROCESSING',
      'COMPLETED',
    ];
    let callCount = 0;

    const mockFetcher = vi.fn().mockImplementation(async () => {
      const currentStatus = statuses[callCount] || 'COMPLETED';
      callCount++;
      return { id: 'j-1', status: currentStatus };
    });

    render(<PollingTester fetcher={mockFetcher} intervalMs={100} maxDurationMs={5000} />);

    // 1st call (immediate on mount) -> PENDING
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('status')).toHaveTextContent('PENDING');
    expect(screen.getByTestId('completed')).toHaveTextContent('NO');
    expect(mockFetcher).toHaveBeenCalledTimes(1);

    // Advance 100ms -> 2nd call -> PROCESSING
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(screen.getByTestId('status')).toHaveTextContent('PROCESSING');
    expect(screen.getByTestId('completed')).toHaveTextContent('NO');
    expect(mockFetcher).toHaveBeenCalledTimes(2);

    // Advance 100ms -> 3rd call -> COMPLETED (terminal)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(screen.getByTestId('status')).toHaveTextContent('COMPLETED');
    expect(screen.getByTestId('completed')).toHaveTextContent('YES');
    expect(mockFetcher).toHaveBeenCalledTimes(3);

    // Advance another 500ms -> should NOT call anymore because it reached terminal state
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(mockFetcher).toHaveBeenCalledTimes(3);

    vi.useRealTimers();
  });

  it('stops polling and sets isTimeout to true when maxDurationMs is exceeded', async () => {
    vi.useFakeTimers();

    const mockFetcher = vi.fn().mockResolvedValue({ id: 'j-2', status: 'PROCESSING' });

    render(<PollingTester fetcher={mockFetcher} intervalMs={100} maxDurationMs={350} />);

    // 1st call on mount
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId('timeout')).toHaveTextContent('NO');

    // Advance past maxDurationMs (400ms)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });

    expect(screen.getByTestId('timeout')).toHaveTextContent('YES');
    const finalCalls = mockFetcher.mock.calls.length;

    // Advance more -> should not make more calls
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });
    expect(mockFetcher.mock.calls.length).toBe(finalCalls);

    vi.useRealTimers();
  });
});
