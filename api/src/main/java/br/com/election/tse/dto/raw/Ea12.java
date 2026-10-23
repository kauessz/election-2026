package br.com.election.tse.dto.raw;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown=true)
public record Ea12(List<Region> abr) {
 @JsonIgnoreProperties(ignoreUnknown=true) public record Region(String cd,List<Municipality> mu) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Municipality(String cd,String nm,List<String> z) {}
}
