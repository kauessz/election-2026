"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, apiError } from "./api";
import { ApiState, createApiRefresh, initialApiState } from "./api-refresh";

export function useApi<T>(path: string, { refreshMs }: { refreshMs?: number } = {}) {
  const [state, setState] = useState<ApiState<T> & { path: string }>(() => ({ path, ...initialApiState<T>() }));
  const request = useRef<{ path: string; refresh: () => void } | null>(null);
  useEffect(() => {
    const session = createApiRefresh<T>({
      load: (signal) => api.get<T>(path, { signal }).then((response) => response.data),
      notify: (next) => setState({ path, ...next }),
      errorMessage: apiError,
      refreshMs,
    });
    request.current = { path, refresh: session.refresh };
    session.refresh();
    return () => { session.dispose(); request.current = null; };
  }, [path, refreshMs]);
  const refresh = useCallback(() => {
    if (request.current?.path === path) request.current.refresh();
  }, [path]);
  return { ...(state.path === path ? state : initialApiState<T>()), retry: refresh, refresh };
}
