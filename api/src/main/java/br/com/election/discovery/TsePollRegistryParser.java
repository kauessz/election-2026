package br.com.election.discovery;

import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

@Component
public class TsePollRegistryParser {
    private final DiscoveryConfiguration.Properties properties;
    public TsePollRegistryParser(DiscoveryConfiguration.Properties properties){this.properties=properties;}
    public record Parsed(int scanned,List<Entry> entries){}
    public static final int MAX_EXPANDED=96*1024*1024, MAX_CSV=64*1024*1024;
    public record Entry(String registrationNumber,String pollsterName,String legalName,LocalDate fieldStart,LocalDate fieldEnd,
        LocalDate expectedPublicationDate,Integer sampleSize,String methodology,Map<String,String> metadata){}
    private static final List<String> REQUIRED=List.of("AA_ELEICAO","SG_UE","SG_UF","DS_CARGO","NR_PROTOCOLO_REGISTRO","NM_EMPRESA",
        "NM_EMPRESA_FANTASIA","NM_UE","DT_INICIO_PESQUISA","DT_FIM_PESQUISA","DT_DIVULGACAO","QT_ENTREVISTADO","DS_METODOLOGIA_PESQUISA","DS_PLANO_AMOSTRAL","DS_DADO_MUNICIPIO");
    public List<Entry> parse(byte[] bytes){return inspect(bytes).entries();}
    public Parsed inspect(byte[] bytes){
        if(bytes.length>TsePollRegistryClient.MAX_ZIP)throw new IllegalArgumentException("ZIP excede limite.");
        byte[] csv=null;int total=0,entries=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>64)throw new IllegalArgumentException("ZIP com entradas demais.");
                boolean selected=entry.getName().equals("pesquisa_eleitoral_2026_BRASIL.csv");
                var output=selected?new ByteArrayOutputStream():null;int size=0,n;
                while((n=zip.read(buffer))!=-1){total+=n;size+=n;
                    if(total>MAX_EXPANDED || size>MAX_CSV)throw new IllegalArgumentException("Limite de expansão do ZIP excedido.");
                    if(selected)output.write(buffer,0,n);
                }
                if(selected){if(csv!=null)throw new IllegalArgumentException("CSV nacional duplicado no ZIP.");csv=output.toByteArray();}
            }
        }catch(IOException e){throw new IllegalArgumentException("ZIP oficial inválido.");}
        if(csv==null)throw new IllegalArgumentException("CSV pesquisa_eleitoral_2026_BRASIL.csv não encontrado; contrato mudou.");
        var records=csv(decode(csv));
        return new Parsed(Math.max(0,records.size()-1),parseRecords(records));
    }
    private String decode(byte[] data){
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString().replaceFirst("^\\uFEFF","");}
        catch(CharacterCodingException e){return new String(data,StandardCharsets.ISO_8859_1);}
    }
    List<Entry> parseCsv(String text){
        return parseRecords(csv(text));
    }
    private List<Entry> parseRecords(List<List<String>> records){
        if(records.isEmpty() || !records.getFirst().containsAll(REQUIRED))throw new IllegalArgumentException("Cabeçalho oficial incompleto; sync cancelado.");
        var headers=records.getFirst();if(new HashSet<>(headers).size()!=headers.size())throw new IllegalArgumentException("Cabeçalhos duplicados.");
        Map<String,Entry> found=new LinkedHashMap<>();
        for(int i=1;i<records.size();i++){
            var values=records.get(i);if(values.size()!=headers.size())throw new IllegalArgumentException("Linha CSV com quantidade de campos inválida: "+(i+1));
            Map<String,String> raw=new LinkedHashMap<>();for(int j=0;j<headers.size();j++)raw.put(headers.get(j),values.get(j));
            if(!"2026".equals(raw.get("AA_ELEICAO")) || !"BR".equals(raw.get("SG_UE")) || !"BR".equals(raw.get("SG_UF"))
                || !List.of("presidente","presidentedarepublica").contains(normalize(raw.get("DS_CARGO")))
                || !nationalScope(raw))continue;
            var start=date(raw.get("DT_INICIO_PESQUISA"));var end=date(raw.get("DT_FIM_PESQUISA"));var publication=date(raw.get("DT_DIVULGACAO"));
            // This dataset has no explicit round/embargo columns. Restrict to the interval after round 1;
            // operator confirmation of the scenario and permission remains mandatory before any preview/import.
            if(!start.isAfter(LocalDate.of(2026,10,4)) || end.isAfter(LocalDate.of(2026,10,25)) || end.isBefore(start)
                || publication.isBefore(end) || publication.isBefore(properties.operationalFrom()) || publication.isAfter(LocalDate.of(2026,10,25)))continue;
            String reg=raw.get("NR_PROTOCOLO_REGISTRO").trim();
            if(reg.matches("BR\\d{9}"))reg="BR-"+reg.substring(2,7)+"/"+reg.substring(7);
            if(!reg.matches("BR-\\d{5}/2026"))throw new IllegalArgumentException("Registro presidencial inválido.");
            String name=missing(raw.get("NM_EMPRESA_FANTASIA"))?raw.get("NM_EMPRESA"):raw.get("NM_EMPRESA_FANTASIA");
            Integer n=missing(raw.get("QT_ENTREVISTADO"))?null:Integer.valueOf(raw.get("QT_ENTREVISTADO"));
            if(name.isBlank() || n!=null&&n<=0)throw new IllegalArgumentException("Metadados oficiais inválidos.");
            var item=new Entry(reg,name,raw.get("NM_EMPRESA"),start,end,publication,n,raw.get("DS_METODOLOGIA_PESQUISA"),raw);
            var previous=found.putIfAbsent(reg,item);if(previous!=null&&!previous.equals(item))throw new IllegalArgumentException("Registro conflitante no CSV: "+reg);
        }
        return List.copyOf(found.values());
    }
    // BR/BR/BRASIL identifies the presidential electoral unit, NOT the sampling universe.
    // Require those structured fields first; then conservative evidence of the actual coverage.
    static boolean nationalScope(Map<String,String> raw){
        if(!"brasil".equals(normalize(raw.get("NM_UE"))))return false;
        String coverage=scopeText(raw.get("DS_DADO_MUNICIPIO"));
        String plan=scopeText(raw.get("DS_PLANO_AMOSTRAL"));
        String methodology=scopeText(raw.get("DS_METODOLOGIA_PESQUISA"));
        if(localScope(coverage)||localScope(plan)||localScope(methodology))return false;
        return nationalEvidence(coverage)||nationalEvidence(plan)||nationalEvidence(methodology);
    }
    private static String scopeText(String value){return java.text.Normalizer.normalize(Objects.toString(value,""),java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private static boolean localScope(String text){
        return java.util.regex.Pattern.compile("(?:abrangencia|universo|eleitorado|eleitores|publico.alvo|residentes|aptos a votar|pesquisa (?:sera |foi |e )?realizada).{0,100}?(?:estado d[oa] |distrito federal|municipio de |abrangencia estadual|abrangencia municipal)").matcher(text).find();
    }
    private static boolean nationalEvidence(String text){
        return java.util.regex.Pattern.compile("(?:abrangencia.{0,65}?(?:nacional|(?:ao |o |e |do )brasil)|territorio (?:do brasil|nacional)|eleitorado (?:brasileiro|d[oe] brasil|do pais)|eleitores brasileiros|eleitor(?:es|as).{0,65}?d[oe] brasil|area fisica.{0,60}?(?:o |e )brasil|27 unidades da federacao|(?:cinco|5 \\(cinco\\)) regioes do brasil|pesquisa (?:sera |e )?realizada no brasil)").matcher(text).find();
    }
    private LocalDate date(String value){try{return LocalDate.parse(value.substring(0,10));}catch(Exception e){throw new IllegalArgumentException("Data inválida no CSV oficial.");}}
    private boolean missing(String value){return value==null||value.isBlank()||value.startsWith("#");}
    public static String normalize(String text){return java.text.Normalizer.normalize(Objects.toString(text,""),java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    // RFC-style quoted semicolon CSV, including escaped quotes and embedded newlines. No HTML parsing.
    static List<List<String>> csv(String text){
        var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(c);}
            else if(c=='"'){if(!cell.isEmpty()||closed)throw new IllegalArgumentException("Aspas CSV inválidas.");quoted=true;}
            else if(c==';'){row.add(cell.toString());cell.setLength(0);closed=false;}
            else if(c=='\n'||c=='\r'){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString());
                if(row.size()>1||!row.getFirst().isEmpty())rows.add(List.copyOf(row));row.clear();cell.setLength(0);closed=false;
                if(rows.size()>50000)throw new IllegalArgumentException("CSV com linhas demais.");}
            else {if(closed)throw new IllegalArgumentException("Conteúdo após aspas CSV.");cell.append(c);}
            if(cell.length()>30000||row.size()>100)throw new IllegalArgumentException("Campo CSV excede limite.");
        }
        if(quoted)throw new IllegalArgumentException("CSV com aspas não fechadas.");
        if(!cell.isEmpty()||!row.isEmpty()){row.add(cell.toString());rows.add(List.copyOf(row));}return rows;
    }
}
