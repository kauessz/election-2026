"use client";
import { useEffect, useState } from "react";
import { updateAgeSeconds } from "@/lib/live-counting";

export function LiveUpdateStamp({ timestamp, projection = false, source }: { timestamp: string | null | undefined; projection?: boolean; source: string }) {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const age = now === null ? null : updateAgeSeconds(timestamp, now);
  const absolute = timestamp && Number.isFinite(Date.parse(timestamp)) ? new Date(timestamp).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" }) : null;
  // No aria-live: a ticking visual clock should not interrupt screen readers every second.
  return <div className="live-update-stamp">
    <span>{age === null ? "Horário da atualização: " + (absolute ? "abaixo" : "indisponível") : `${projection ? "Atualizada" : "Atualizado"} há ${age.toLocaleString("pt-BR")} ${age === 1 ? "segundo" : "segundos"}`}</span>
    <small>{source}: {absolute ? <time dateTime={timestamp!}>{absolute} (Brasília)</time> : "indisponível"}</small>
  </div>;
}
