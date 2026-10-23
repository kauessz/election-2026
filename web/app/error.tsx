"use client";
import { ErrorState } from "@/components/poll-list";
export default function Error({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorState message="Ocorreu um erro ao abrir esta página." retry={reset} />
  );
}
