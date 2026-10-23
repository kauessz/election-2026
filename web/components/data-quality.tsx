"use client";
import Link from "next/link";
import { useApi } from "@/lib/use-api";
import type { Quality } from "@/lib/pre-election";
export function DataQuality(){const q=useApi<Quality>("/api/admin/data-quality");return <section className="freeze-panel"><h2>Qualidade dos dados</h2><button className="action-button" onClick={q.retry} disabled={q.loading}>Verificar novamente</button>{q.loading&&<p role="status">Verificando…</p>}{q.error&&<p role="alert">{q.error}</p>}{q.data&&<><p><strong>{q.data.status}</strong> · {q.data.ready?"Nenhum ERROR; revisão de avisos recomendada.":"Corrija os ERRORs antes de congelar."}</p><ul>{q.data.issues.map((i,n)=><li key={n}><strong>{i.severity} · {i.code}</strong>: {i.message} {i.entityId&&<code>{i.entityId}</code>}</li>)}</ul>{q.data.ready?<Link className="text-link" href="/admin/freeze">Criar freeze pré-eleição</Link>:<button disabled>Criar freeze pré-eleição</button>}</>}</section>;}
