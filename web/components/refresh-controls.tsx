"use client";
import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { updatedTime } from "@/lib/presentation";

export function RefreshControls({ lastUpdatedAt, loading, refreshing, error, hasData, refresh, label = "Atualizar dados" }: {
  lastUpdatedAt: string | null; loading: boolean; refreshing: boolean;
  error: string | null; hasData: boolean; refresh: () => void; label?: string;
}) {
  return <div className="refresh-controls">
    <div>
      <p className="refresh-time" role="status">{lastUpdatedAt ? `Atualizado em ${updatedTime(lastUpdatedAt)}` : "Aguardando primeira atualização"}{refreshing && " · Atualizando…"}</p>
      <p className="chart-note">Horário de Brasília. Atualiza os dados do sistema, sem consultar os sites dos institutos.</p>
      {error && hasData && <p className="refresh-error" role="status">Não foi possível atualizar. Últimos dados preservados. {error}</p>}
    </div>
    <Button variant="outline" onClick={refresh} disabled={loading || refreshing}>
      <RefreshCw size={15} className={refreshing ? "refresh-spinning" : undefined} aria-hidden="true" />{label}
    </Button>
  </div>;
}
