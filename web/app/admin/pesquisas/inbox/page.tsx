"use client";
import { FormEvent,useState } from "react";
import Link from "next/link";
import { api,apiError,type Pollster } from "@/lib/api";
import { canApproveDiscovery,discoveryStatusLabels,terminalDiscovery,type Discovery,type DiscoveryInbox } from "@/lib/poll-discovery";
import type { ImportReport } from "@/lib/pre-election";
import "./inbox.css";

type Request=<T>(method:"get"|"post"|"put",path:string,body?:unknown)=>Promise<T>;
const date=(value:string|null)=>value?new Date(value.length===10?`${value}T12:00:00-03:00`:value).toLocaleString("pt-BR",{timeZone:"America/Sao_Paulo",...(value.length===10?{dateStyle:"short" as const}:{dateStyle:"short" as const,timeStyle:"short" as const})}):"Não informado";

function ReviewForm({item,pollsters,request,onUpdate,busy,setBusy}:{item:Discovery;pollsters:Pollster[];request:Request;onUpdate:(item:Discovery)=>Promise<void>;busy:boolean;setBusy:(value:boolean)=>void}){
  const [dirty,setDirty]=useState(false),[approved,setApproved]=useState(false),[error,setError]=useState<string|null>(null);
  const [pendingAction,setPendingAction]=useState<"import"|"ignore"|null>(null),[ignoreReason,setIgnoreReason]=useState("");
  const [report,setReport]=useState<ImportReport|null>(item.importReport??item.dryRunReport);
  async function save(event:FormEvent<HTMLFormElement>){
    event.preventDefault();const form=new FormData(event.currentTarget);const text=(key:string)=>String(form.get(key)||"").trim();
    setBusy(true);setError(null);
    try{const data=await request<Discovery>("put",`/${item.id}/results`,{
      pollsterSlug:text("pollsterSlug"),fieldStart:text("fieldStart"),fieldEnd:text("fieldEnd"),publishedAt:text("publishedAt"),
      sampleSize:text("sampleSize")?Number(text("sampleSize")):null,marginOfError:text("marginOfError")?Number(text("marginOfError")):null,
      methodology:text("methodology")||null,sourceUrl:text("sourceUrl"),voteBasis:text("voteBasis"),notes:text("notes")||null,
      candidateResults:[22,13].map(ballotNumber=>({ballotNumber,percentage:Number(text(`candidate-${ballotNumber}`))})),
      secondRoundConfirmed:form.has("secondRoundConfirmed"),publicationAllowedConfirmed:form.has("publicationAllowedConfirmed"),
    });await onUpdate(data);setDirty(false);setApproved(false);setReport(null);}catch(e){setError(apiError(e));}finally{setBusy(false);}
  }
  async function action(kind:"dry-run"|"import"|"ignore"){
    let body:unknown;
    if(kind==="import"){
      if(!canApproveDiscovery(item,dirty,approved))return;
      if(pendingAction!=="import"){setPendingAction("import");return;}
      body={payloadHash:item.dryRunHash,approved:true};
    }
    if(kind==="ignore"){
      if(pendingAction!=="ignore"){setPendingAction("ignore");return;}
      if(!ignoreReason.trim()){setError("Informe o motivo para ignorar.");return;}body={notes:ignoreReason.trim()};
    }
    setBusy(true);setError(null);
    try{
      const result=await request<ImportReport|{report:ImportReport;snapshots:number}>("post",`/${item.id}/${kind}`,body);
      if(kind==="dry-run")setReport(result as ImportReport);
      await onUpdate(await request<Discovery>("get",`/${item.id}`));setApproved(false);
    }catch(e){setError(apiError(e));}finally{setBusy(false);setPendingAction(null);}
  }
  return <section className="discovery-review" aria-label="Revisão da descoberta">
    <h2>{item.pollsterName} · {item.registrationNumber}</h2><p><strong>{discoveryStatusLabels[item.status]}</strong> · Detectada: {date(item.detectedAt)} · Revisada: {date(item.reviewedAt)}</p>
    <p>Divulgação permitida a partir da data cadastral: {date(item.expectedPublicationDate)}. Isso não comprova horário real, turno ou ausência de bloqueio judicial.</p>
    {item.importedPollId&&<p>Pesquisa importada: <code>{item.importedPollId}</code></p>}
    {item.importReport&&<p role="status">Importação confirmada: created={item.importReport.created}, updated={item.importReport.updated}, unchanged={item.importReport.unchanged}, failed={item.importReport.failed}. Snapshots criados: {item.snapshotIds?.length??0}.</p>}
    {item.status==="IMPORTED"&&!item.importReport&&<p>Registro já encontrado na base operacional. Nenhuma importação ou snapshot foi executado por esta descoberta.</p>}
    {item.sourceUrl&&<p><a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">Publicação revisada ↗</a> · Publicada em {date(item.publishedAt)}</p>}
    <details><summary>Metadados originais do TSE (somente leitura)</summary><pre>{JSON.stringify(item.tseMetadata,null,2)}</pre><h3>Último cadastro observado</h3><pre>{JSON.stringify(item.latestTseMetadata,null,2)}</pre></details>
    {item.reviewedAt&&<details><summary>Conteúdo revisado pelo operador</summary><pre>{JSON.stringify({pollsterSlug:item.pollsterSlug,fieldStart:item.fieldStart,fieldEnd:item.fieldEnd,publishedAt:item.publishedAt,sampleSize:item.sampleSize,marginOfError:item.marginOfError,methodology:item.methodology,sourceUrl:item.sourceUrl,voteBasis:item.voteBasis,candidateResults:item.candidateResults,notes:item.notes},null,2)}</pre></details>}
    {!terminalDiscovery(item)&&<form onSubmit={save} onChange={()=>{setDirty(true);setApproved(false);setReport(null);setPendingAction(null);}}>
      <p className="discovery-notice">Percentuais e fonte da publicação são fornecidos e revisados pelo operador. Não há scraping editorial nem importação automática.</p>
      <div className="discovery-fields">
        <label>Instituto associado<select name="pollsterSlug" required defaultValue={item.pollsterSlug||""} disabled={busy}><option value="">Selecione um instituto existente</option>{pollsters.filter(p=>p.active).map(p=><option key={p.id} value={p.slug}>{p.name} · {p.slug}</option>)}</select></label>
        <p>Sem correspondência? Cadastre explicitamente pelo endpoint administrativo <code>POST /api/admin/pollsters</code>, com nome e slug verificados, e recarregue a inbox. Nenhum instituto será criado silenciosamente. <Link href="/admin/institutos">Consultar institutos</Link>.</p>
        <label>Início do campo<input name="fieldStart" type="date" required defaultValue={item.fieldStart} disabled={busy}/></label>
        <label>Fim do campo<input name="fieldEnd" type="date" required defaultValue={item.fieldEnd} disabled={busy}/></label>
        <label>Amostra<input name="sampleSize" type="number" min="1" step="1" defaultValue={item.sampleSize??""} disabled={busy}/></label>
        <label>Margem (p.p., se declarada)<input name="marginOfError" type="number" min="0.0001" max="100" step="0.0001" defaultValue={item.marginOfError??""} disabled={busy}/></label>
        <label>Fonte pública da divulgação<input name="sourceUrl" type="url" required maxLength={2000} defaultValue={item.sourceUrl||""} disabled={busy}/></label>
        <label>Publicação real (ISO com fuso)<input name="publishedAt" type="text" required placeholder="AAAA-MM-DDTHH:mm:ss-03:00" defaultValue={item.publishedAt||""} disabled={busy}/><small>Se não houver horário verificável, registre a pendência; não invente um Instant.</small></label>
        <label>Base<select name="voteBasis" required defaultValue={item.voteBasis||""} disabled={busy}><option value="">Confirme a base</option><option value="TOTAL">TOTAL</option><option value="VALID">VALID</option></select></label>
        {([[22,"Flávio Bolsonaro"],[13,"Lula"]] as const).map(([ballotNumber,name])=><label key={ballotNumber}>{name} · {ballotNumber}<input name={`candidate-${ballotNumber}`} type="number" min="0" max="100" step="0.0001" required defaultValue={item.candidateResults?.find(r=>r.ballotNumber===ballotNumber)?.percentage??""} disabled={busy}/></label>)}
      </div>
      <label>Metodologia confirmada<textarea name="methodology" maxLength={5000} defaultValue={item.methodology||""} disabled={busy}/></label>
      <label>Notas e pendências<textarea name="notes" maxLength={5000} defaultValue={item.notes||""} disabled={busy}/></label>
      <label className="discovery-check"><input type="checkbox" name="secondRoundConfirmed" required defaultChecked={item.secondRoundConfirmed} disabled={busy}/> Confirmei que a pesquisa é nacional, presidencial de 2026 e do segundo turno para os candidatos 13/22.</label>
      <label className="discovery-check"><input type="checkbox" name="publicationAllowedConfirmed" required defaultChecked={item.publicationAllowedConfirmed} disabled={busy}/> Conferi no PesqEle a permissão de divulgação e eventuais decisões judiciais.</label>
      <button className="action-button" disabled={busy}>Salvar revisão no staging</button>
    </form>}
    {!terminalDiscovery(item)&&<div className="discovery-actions">
      <button className="action-button" disabled={busy||dirty||item.status!=="READY_FOR_REVIEW"} onClick={()=>action("dry-run")}>Executar dry-run</button>
      <label className="discovery-check"><input type="checkbox" checked={approved} disabled={busy||dirty||!item.dryRunHash} onChange={e=>setApproved(e.target.checked)}/> Aprovo os dados, fontes e normalização deste dry-run</label>
      <button className="action-button" disabled={busy||!canApproveDiscovery(item,dirty,approved)} onClick={()=>action("import")}>Importar pesquisa aprovada</button>
      <button className="action-button" disabled={busy} onClick={()=>action("ignore")}>Ignorar</button>
    </div>}
    {pendingAction&&!terminalDiscovery(item)&&<section className="discovery-notice" aria-label="Confirmação administrativa">
      <p>{pendingAction==="import"?`Confirmar a importação de ${item.registrationNumber} e criar snapshots dos quatro modelos?`:"Ignorar esta descoberta preservará o staging e registrará o motivo na auditoria."}</p>
      {pendingAction==="ignore"&&<label>Motivo para ignorar<textarea maxLength={5000} value={ignoreReason} onChange={e=>setIgnoreReason(e.target.value)} disabled={busy}/></label>}
      <div className="discovery-actions"><button className="action-button" disabled={busy||(pendingAction==="import"?!canApproveDiscovery(item,dirty,approved):!ignoreReason.trim())} onClick={()=>action(pendingAction)}>Confirmar {pendingAction==="import"?"importação":"ignore"}</button><button className="action-button" disabled={busy} onClick={()=>setPendingAction(null)}>Cancelar</button></div>
    </section>}
    {dirty&&<p role="status">Edição pendente: salve e execute novo dry-run antes de importar.</p>}
    {error&&<p role="alert">{error}</p>}
    {(report||item.dryRunReport)&&<details open><summary>Relatório do importador existente</summary><pre>{JSON.stringify(report||item.dryRunReport,null,2)}</pre></details>}
    {item.notes&&terminalDiscovery(item)&&<p>Notas: {item.notes}</p>}
  </section>;
}

export default function DiscoveryInboxPage(){
  const [key,setKey]=useState(""),[inbox,setInbox]=useState<DiscoveryInbox|null>(null),[pollsters,setPollsters]=useState<Pollster[]>([]),[selected,setSelected]=useState<Discovery|null>(null);
  const [status,setStatus]=useState(""),[filterDate,setFilterDate]=useState(""),[pollster,setPollster]=useState("");
  const [busy,setBusy]=useState(false),[error,setError]=useState<string|null>(null),[message,setMessage]=useState("");
  const request:Request=async(method,path,body)=>{const response=await api.request({method,url:`/api/admin/poll-discoveries${path}`,data:body,headers:{"X-Admin-Key":key},timeout:45000});return response.data;};
  async function load(){const query=new URLSearchParams();if(status)query.set("status",status);if(filterDate)query.set("expectedPublicationDate",filterDate);if(pollster.trim())query.set("pollster",pollster.trim());
    const [list,institutes]=await Promise.all([request<DiscoveryInbox>("get",`?${query}`),api.get<Pollster[]>("/api/pollsters").then(r=>r.data)]);setInbox(list);setPollsters(institutes);}
  async function run(action:()=>Promise<void>){setBusy(true);setError(null);try{await action();}catch(e){setError(apiError(e));}finally{setBusy(false);}}
  return <div className="discovery-inbox"><span className="section-kicker">ADMINISTRAÇÃO / INBOX SEMIAUTOMÁTICA</span><h1>Descoberta de pesquisas</h1>
    <nav className="discovery-actions"><Link href="/admin/importacao">Importadores</Link><Link href="/admin/pesquisas">Pesquisas operacionais</Link><Link href="/admin/institutos">Institutos</Link></nav>
    <p>Descoberta → staging → revisão humana → dry-run → aprovação → importação. Sync e revisão nunca alteram pesquisas operacionais. Uma importação aprovada cria snapshots dos quatro modelos.</p>
    <label className="discovery-key">X-Admin-Key<input type="password" autoComplete="off" value={key} disabled={busy} onChange={e=>{setKey(e.target.value);setInbox(null);setSelected(null);}}/><small>Usada somente nesta página, em memória. Não é armazenada nem incluída em URLs.</small></label>
    <div className="discovery-actions"><button className="action-button" disabled={busy} onClick={()=>run(load)}>Carregar inbox</button><button className="action-button" disabled={busy} onClick={()=>run(async()=>{setSelected(null);const report=await request<{created:number;updated:number;unchanged:number}>("post","/sync");setMessage(`Sync: ${report.created} novas, ${report.updated} atualizadas, ${report.unchanged} sem alteração. Nenhuma pesquisa importada.`);await load();})}>Sincronizar TSE</button></div>
    <div className="discovery-fields"><label>Status<select value={status} disabled={busy} onChange={e=>setStatus(e.target.value)}><option value="">Todos</option>{Object.entries(discoveryStatusLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
      <label>Divulgação prevista<input type="date" value={filterDate} disabled={busy} onChange={e=>setFilterDate(e.target.value)}/></label><label>Instituto (nome ou slug)<input value={pollster} disabled={busy} onChange={e=>setPollster(e.target.value)}/></label><button className="action-button" disabled={busy} onClick={()=>run(load)}>Aplicar filtros</button></div>
    {busy&&<p role="status">Processando…</p>}{error&&<p role="alert">{error}</p>}{message&&<p role="status">{message}</p>}
    {inbox&&<><div className="discovery-summary">{[["Descobertas adicionadas hoje",inbox.detectedToday],["Divulgação prevista hoje",inbox.expectedPublicationToday],["Aguardando resultado",inbox.counts.AWAITING_RESULTS],["Prontas para revisão",inbox.counts.READY_FOR_REVIEW],["Importadas",inbox.counts.IMPORTED],["Não mapeadas",inbox.counts.UNMAPPED_POLLSTER]].map(([label,value])=><article key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
      <p>Ordem: divulgação prevista hoje, próximas divulgações, pendentes de revisão e importadas. A data prevista não comprova publicação efetiva.</p><div className="discovery-table"><table><thead><tr>{["Instituto","Registro","Campo","Amostra","Margem","Divulgação","Status","Ação"].map(label=><th key={label}>{label}</th>)}</tr></thead><tbody>{inbox.items.map(item=><tr key={item.id}>
        <td>{item.pollsterName}<small>{item.pollsterSlug||"Associação administrativa necessária"}</small></td><td>{item.registrationNumber}</td><td>{date(item.fieldStart)} a {date(item.fieldEnd)}</td><td>{item.sampleSize??"—"}</td><td>{item.marginOfError??"Não estruturada no CSV"}</td><td>{date(item.expectedPublicationDate)}</td><td>{discoveryStatusLabels[item.status]}</td><td><button disabled={busy} onClick={()=>run(async()=>setSelected(await request<Discovery>("get",`/${item.id}`)))}>{terminalDiscovery(item)?"Ver auditoria":item.status==="AWAITING_RESULTS"?"Informar resultado":"Revisar"}</button></td>
      </tr>)}</tbody></table>{!inbox.items.length&&<p>Nenhuma descoberta para estes filtros.</p>}</div></>}
    {selected&&<ReviewForm key={`${selected.id}:${selected.updatedAt}`} item={selected} pollsters={pollsters} request={request} busy={busy} setBusy={setBusy} onUpdate={async item=>{setSelected(item);await load();}}/>}
  </div>;
}
