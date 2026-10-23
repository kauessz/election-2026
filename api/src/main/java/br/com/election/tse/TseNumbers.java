package br.com.election.tse;
import java.math.BigDecimal;
import br.com.election.tse.exception.TseParsingException;
public final class TseNumbers {
 private TseNumbers() {}
 public static BigDecimal percentage(String value, String field) {
  if(value==null || value.isBlank()) return null;
  if(!value.matches("[0-9]+([,.][0-9]+)?")) throw invalid(field,value);
  BigDecimal n=new BigDecimal(value.replace(',','.'));
  if(n.compareTo(BigDecimal.valueOf(100))>0) throw invalid(field,value);
  return n;
 }
 public static Long count(String value, String field) {
  if(value==null || value.isBlank()) return null;
  try { if(!value.matches("[0-9]+")) throw invalid(field,value); return Long.valueOf(value); }
  catch(NumberFormatException e) { throw invalid(field,value); }
 }
 public static long required(String value,String field) {
  Long n=count(value,field); if(n==null) throw new TseParsingException("EA20: campo obrigatório ausente: "+field); return n;
 }
 private static TseParsingException invalid(String f,String v) { return new TseParsingException("EA20: número inválido em "+f+": "+v); }
}
