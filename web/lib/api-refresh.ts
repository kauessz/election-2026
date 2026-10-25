export type ApiState<T> = {
  data: T | null;
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  lastUpdatedAt: string | null;
};
export const initialApiState = <T>(): ApiState<T> => ({
  data: null, loading: true, refreshing: false, error: null, lastUpdatedAt: null,
});

/** One request at a time; polling waits for completion, including failed attempts. */
export function createApiRefresh<T>(options: {
  load: (signal: AbortSignal) => Promise<T>;
  notify: (state: ApiState<T>) => void;
  errorMessage: (error: unknown) => string;
  refreshMs?: number;
  now?: () => number;
  isVisible?: () => boolean;
  retainErrorWhileRefreshing?: boolean;
}) {
  let state = initialApiState<T>();
  let pending = false;
  let disposed = false;
  let controller: AbortController | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const emit = (next: ApiState<T>) => { state = next; options.notify(next); };

  const refresh = () => {
    if (disposed || pending || options.isVisible?.() === false) return;
    clearTimeout(timer);
    pending = true;
    controller = new AbortController();
    const signal = controller.signal;
    emit({ ...state, loading: state.lastUpdatedAt === null, refreshing: state.lastUpdatedAt !== null, error: options.retainErrorWhileRefreshing ? state.error : null });
    void Promise.resolve().then(() => {
      if (signal.aborted) return;
      return options.load(signal);
    }).then((data) => {
      if (!disposed && !signal.aborted) emit({
        data: data as T, loading: false, refreshing: false, error: null,
        lastUpdatedAt: new Date((options.now ?? Date.now)()).toISOString(),
      });
    }).catch((error: unknown) => {
      if (!disposed && !signal.aborted) emit({ ...state, loading: false, refreshing: false, error: options.errorMessage(error) });
    }).finally(() => {
      pending = false;
      if (!disposed && options.isVisible?.() !== false && options.refreshMs && Number.isFinite(options.refreshMs) && options.refreshMs > 0)
        timer = setTimeout(refresh, options.refreshMs);
    });
  };

  return {
    refresh,
    pause: () => { clearTimeout(timer); },
    dispose: () => { disposed = true; clearTimeout(timer); controller?.abort(); },
  };
}
