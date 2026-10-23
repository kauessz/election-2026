package br.com.election.tse;
import org.springframework.stereotype.Component;
@Component
public class TseUrlBuilder {
 private final TseResultsProperties p;
 public TseUrlBuilder(TseResultsProperties p) { this.p=p; }
 private String root(){return "/"+p.environment()+"/ele"+p.year()+"/"+p.electionId();}
 private String suffix(){return "-c"+p.presidentOfficeCode()+"-e"+String.format("%06d",Integer.parseInt(p.electionId()))+"-u.json";}
 public String baseline(OfficialElectionResult.Scope scope,String electionId){
  if(!electionId.matches("[0-9]{1,6}"))throw new IllegalArgumentException("Eleição inválida");
  String code=switch(scope.type()){case NATIONAL->"br";case STATE->BrazilState.parse(scope.code()).name().toLowerCase(java.util.Locale.ROOT);default->throw new IllegalArgumentException("Baseline apenas Brasil e UF");};
  return "/"+p.environment()+"/ele"+p.year()+"/"+electionId+"/dados/"+code+"/"+code+"-c"+p.presidentOfficeCode()+"-e"+String.format("%06d",Integer.parseInt(electionId))+"-u.json";
 }
 // Verified against the official EA20 endpoint (UF scope ZZ).
 public String overseas(){return root()+"/dados/zz/zz"+suffix();}
 public String national(){return root()+"/dados/br/br"+suffix();}
 public String state(BrazilState uf){String u=uf.name().toLowerCase(java.util.Locale.ROOT);return root()+"/dados/"+u+"/"+u+suffix();}
 public String municipalities(){return root()+"/config/mun-e"+String.format("%06d",Integer.parseInt(p.electionId()))+"-cm.json";}
 // Only catalogue-issued municipality contracts may reach this builder through the service.
 public String municipality(MunicipalityCatalogue.Municipality m){return territorial(m,"");}
 public String zone(MunicipalityCatalogue.Municipality m,String zone){
  if(!m.zones().contains(zone)) throw new IllegalArgumentException("Zona ausente no EA12");
  return territorial(m,"-z"+zone);
 }
 private String territorial(MunicipalityCatalogue.Municipality m,String zone){
  if(!m.code().matches("[0-9]{5}")) throw new IllegalArgumentException("Código TSE deve conter cinco dígitos");
  String u=m.uf().name().toLowerCase(java.util.Locale.ROOT);return root()+"/dados/"+u+"/"+u+m.code()+zone+suffix();
 }
}
