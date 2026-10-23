package br.com.election.tse;
import java.util.Locale;
public enum BrazilState {
 AC("Acre"), AL("Alagoas"), AP("Amapá"), AM("Amazonas"), BA("Bahia"), CE("Ceará"), DF("Distrito Federal"), ES("Espírito Santo"), GO("Goiás"), MA("Maranhão"), MT("Mato Grosso"), MS("Mato Grosso do Sul"), MG("Minas Gerais"), PA("Pará"), PB("Paraíba"), PR("Paraná"), PE("Pernambuco"), PI("Piauí"), RJ("Rio de Janeiro"), RN("Rio Grande do Norte"), RS("Rio Grande do Sul"), RO("Rondônia"), RR("Roraima"), SC("Santa Catarina"), SP("São Paulo"), SE("Sergipe"), TO("Tocantins");
 public final String displayName;
 BrazilState(String n) { displayName=n; }
 public static BrazilState parse(String value) {
  try { return valueOf(value.toUpperCase(Locale.ROOT)); }
  catch(Exception e) { throw new IllegalArgumentException("UF brasileira inválida: " + value); }
 }
}
