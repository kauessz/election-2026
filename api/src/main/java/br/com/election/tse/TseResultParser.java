package br.com.election.tse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import br.com.election.tse.dto.raw.Ea20;
import br.com.election.tse.exception.TseParsingException;
import java.time.*;
import java.time.format.*;
import java.util.*;
import static br.com.election.tse.OfficialElectionResult.*;
import static br.com.election.tse.TseNumbers.*;
@Component
public class TseResultParser {
 private final JsonMapper json; private final TseResultsProperties properties;
 public TseResultParser(JsonMapper json,TseResultsProperties properties) { this.json=json;this.properties=properties; }
 public OfficialElectionResult parse(String body,Scope scope) { return parse(body,scope,properties.electionId(),2); }
 public OfficialElectionResult parse(String body,Scope scope,String electionId,int round) {
  try {
   Ea20 r=json.readValue(body,Ea20.class);
   String type=switch(scope.type()){case NATIONAL->"br";case STATE->"uf";case MUNICIPALITY->"mu";case ZONE->"zona";};
   String code=scope.code().substring(scope.code().lastIndexOf(':')+1);
   if(!electionId.equals(r.ele()) || !String.valueOf(round).equals(r.t()) || !"o".equals(r.f()) ||
      !type.equals(r.tpabr()) || !code.equalsIgnoreCase(r.cdabr())) throw new TseParsingException("EA20: eleição, turno, fase ou abrangência inesperados");
   Instant generated=LocalDateTime.parse(r.dg()+" "+r.hg(),DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
   if(r.s()==null || r.e()==null || r.v()==null) throw new TseParsingException("EA20: totais de seções, eleitorado ou votos ausentes");
   var office=list(r.carg()).stream().filter(c->required(c.cd(),"cargo.cd")==Integer.parseInt(properties.presidentOfficeCode())).findFirst().orElseThrow(()->new TseParsingException("EA20: cargo presidencial ausente"));
   List<CandidateResult> candidates=new ArrayList<>(); Set<String> numbers=new HashSet<>();
   for(var group:list(office.agr())) for(var party:list(group.par())) for(var c:list(party.cand())) {
    required(c.n(),"cand.n");
    if(!numbers.add(c.n())) throw new TseParsingException("EA20: número eleitoral duplicado");
    candidates.add(new CandidateResult(c.n(),c.sqcand(),c.nm(),c.nmu(),required(c.vap(),"cand.vap"),percentage(c.pvap(),"cand.pvap"),percentage(c.pvapn(),"cand.pvapn"),null));
   }
   if(candidates.isEmpty()) throw new TseParsingException("EA20: candidatos ausentes");
   candidates.sort(Comparator.comparing(CandidateResult::candidateNumber));
   return new OfficialElectionResult(r.ele(),round,scope,generated,r.idg(),"s".equals(r.tf()),
    new Sections(required(r.s().ts(),"s.ts"),required(r.s().st(),"s.st"),percentage(r.s().pst(),"s.pst"),count(r.s().snt(),"s.snt")),
    new Electorate(required(r.e().te(),"e.te"),count(r.e().est(),"e.est"),percentage(r.e().pest(),"e.pest"),count(r.e().a(),"e.a"),percentage(r.e().pa(),"e.pa"),count(r.e().c(),"e.c")),
    new Votes(required(r.v().tv(),"v.tv"),required(r.v().vv(),"v.vv"),count(r.v().vb(),"v.vb"),count(r.v().tvn(),"v.tvn"),count(r.v().vnom(),"v.vnom"),count(r.v().vvc(),"v.vvc")),List.copyOf(candidates),List.of());
  } catch(TseParsingException e) {throw e;}
  catch(Exception e) {throw new TseParsingException("EA20 inválido: "+e.getMessage(),e);}
 }
 private static <T> List<T> list(List<T> value) {return value==null?List.of():value;}
}
