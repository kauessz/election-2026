"use client";
import { useEffect, useState } from "react";
import { api, apiError } from "./api";
import { createApiRefresh, initialApiState, type ApiState } from "./api-refresh";
import { LIVE_POLL_INTERVAL, liveResponseWarning } from "./live-counting";

/** Separate from ElectionContext polling: only API GETs, no source sync or writes. */
export function useLivePolling<T>(path: string) {
  const [state, setState] = useState<ApiState<T> & { path: string }>(() => ({ path, ...initialApiState<T>() }));
  useEffect(() => {
    let previous: T | null = null;
    const session = createApiRefresh<T>({
      refreshMs: LIVE_POLL_INTERVAL,
      isVisible: () => document.visibilityState !== "hidden",
      retainErrorWhileRefreshing: true,
      errorMessage: apiError,
      notify: next => setState({ path, ...next }),
      load: async signal => {
        const { data } = await api.get<T>(path, { signal });
        const warning = liveResponseWarning(previous, data);
        if (warning) throw new Error(warning);
        previous = data;
        return data;
      },
    });
    const onVisibility = () => {
      if (document.visibilityState === "hidden") session.pause();
      else session.refresh();
    };
    document.addEventListener("visibilitychange", onVisibility);
    session.refresh();
    return () => { document.removeEventListener("visibilitychange", onVisibility); session.dispose(); };
  }, [path]);
  return state.path === path ? state : { path, ...initialApiState<T>() };
}
