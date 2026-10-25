import { useCallback, useEffect, useRef, useState } from 'react';

interface UsePollingOptions<T> {
  fn: () => Promise<T>;
  isTerminal: (data: T) => boolean;
  intervalMs?: number;
  maxDurationMs?: number;
  enabled?: boolean;
}

interface UsePollingResult<T> {
  data: T | null;
  loading: boolean;
  error: unknown;
  isTimeout: boolean;
  isCompleted: boolean;
  refresh: () => Promise<void>;
}

export function usePolling<T>({
  fn,
  isTerminal,
  intervalMs = 5000,
  maxDurationMs = 600000, // 10 minutes maximum timeout
  enabled = true,
}: UsePollingOptions<T>): UsePollingResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<unknown>(null);
  const [isTimeout, setIsTimeout] = useState<boolean>(false);
  const [isCompleted, setIsCompleted] = useState<boolean>(false);

  const startTimeRef = useRef<number>(Date.now());
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMountedRef = useRef<boolean>(true);
  const fnRef = useRef(fn);
  fnRef.current = fn;
  const isTerminalRef = useRef(isTerminal);
  isTerminalRef.current = isTerminal;

  const executePoll = useCallback(async () => {
    // If tab is currently hidden, skip polling until it becomes visible
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }

    try {
      const result = await fnRef.current();
      if (!isMountedRef.current) return;

      setData(result);
      setError(null);
      setLoading(false);

      if (isTerminalRef.current(result)) {
        setIsCompleted(true);
        return; // Stop polling on terminal state
      }

      // Check max timeout
      const elapsed = Date.now() - startTimeRef.current;
      if (elapsed >= maxDurationMs) {
        setIsTimeout(true);
        return; // Stop polling on timeout
      }

      // Schedule next poll
      timerRef.current = setTimeout(executePoll, intervalMs);
    } catch (err) {
      if (!isMountedRef.current) return;
      setError(err);
      setLoading(false);
      // Even on temporary error, attempt to poll again unless timed out
      const elapsed = Date.now() - startTimeRef.current;
      if (elapsed >= maxDurationMs) {
        setIsTimeout(true);
      } else {
        timerRef.current = setTimeout(executePoll, intervalMs);
      }
    }
  }, [intervalMs, maxDurationMs]);

  const refresh = useCallback(async () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    startTimeRef.current = Date.now();
    setIsTimeout(false);
    setIsCompleted(false);
    setLoading(true);
    await executePoll();
  }, [executePoll]);

  useEffect(() => {
    isMountedRef.current = true;
    if (!enabled) return;

    startTimeRef.current = Date.now();
    setIsTimeout(false);
    setIsCompleted(false);
    executePoll();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && !isCompleted && !isTimeout) {
        executePoll();
      } else if (document.visibilityState === 'hidden' && timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      isMountedRef.current = false;
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [enabled, executePoll]);

  return {
    data,
    loading,
    error,
    isTimeout,
    isCompleted,
    refresh,
  };
}
