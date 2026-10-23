"use client";
import { useEffect, useState } from "react";
import { api, apiError } from "./api";

export function useApi<T>(path: string) {
  const [state, setState] = useState<{
    data: T | null;
    loading: boolean;
    error: string | null;
  }>({ data: null, loading: true, error: null });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    api
      .get<T>(path, { signal: controller.signal })
      .then(({ data }) => {
        if (!controller.signal.aborted)
          setState({ data, loading: false, error: null });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({ data: null, loading: false, error: apiError(error) });
      });
    return () => controller.abort();
  }, [path, attempt]);
  return {
    ...state,
    retry: () => {
      setState({ data: null, loading: true, error: null });
      setAttempt((a) => a + 1);
    },
  };
}
