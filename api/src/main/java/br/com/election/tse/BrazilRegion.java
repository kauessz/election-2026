package br.com.election.tse;
/** Five Brazilian macroregions; overseas scope ZZ is deliberately not a Brazilian UF. */
public enum BrazilRegion {
 NORTH, NORTHEAST, CENTRAL_WEST, SOUTHEAST, SOUTH;
 public static BrazilRegion of(BrazilState uf){
  return switch(uf){
   case AC,AP,AM,PA,RO,RR,TO -> NORTH;
   case AL,BA,CE,MA,PB,PE,PI,RN,SE -> NORTHEAST;
   case DF,GO,MT,MS -> CENTRAL_WEST;
   case ES,MG,RJ,SP -> SOUTHEAST;
   case PR,RS,SC -> SOUTH;
  };
 }
}
