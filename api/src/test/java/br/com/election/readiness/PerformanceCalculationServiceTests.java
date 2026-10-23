package br.com.election.readiness;

import br.com.election.poll.PollService;
import br.com.election.candidate.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceCalculationServiceTests {
 private final PollService polls=mock(PollService.class);
 private final CandidateRepository candidates=mock(CandidateRepository.class);
 private final PerformanceCalculationService service=new PerformanceCalculationService(polls,candidates);
 private List<ImportModels.CandidateResult> pair(String a,String b){return List.of(new ImportModels.CandidateResult(22,new BigDecimal(a)),new ImportModels.CandidateResult(13,new BigDecimal(b)));}
 private PerformanceCalculationService.Request request(List<ImportModels.CandidateResult> predicted,List<ImportModels.CandidateResult> actual,Boolean complete){return new PerformanceCalculationService.Request(null,null,22,13,true,null,predicted,actual,complete);}
 @Test void partialValidVotePairsKeepTheirOriginalBaseAndUseExistingCandidateAError(){
  var r=service.calculate(request(pair("46","43"),pair("47.03","45.16"),false));
  assertThat(r.pollId()).isNull();assertThat(r.persisted()).isFalse();
  assertThat(r.candidateError()).isEqualByComparingTo("1.03");
  assertThat(r.predictedSpread()).isEqualByComparingTo("3");assertThat(r.actualSpread()).isEqualByComparingTo("1.87");assertThat(r.spreadError()).isEqualByComparingTo("1.13");
  assertThat(r.candidateErrors()).filteredOn(e->e.ballotNumber()==13).singleElement().satisfies(e->assertThat(e.absoluteError()).isEqualByComparingTo("2.16"));
  verifyNoInteractions(polls,candidates);
 }
 @Test void completePairsAcceptToleranceAndBothSubtractInTheSameDirection(){
  var r=service.calculate(request(pair("40","60"),pair("50","49.95"),true));
  assertThat(r.spreadError()).isEqualByComparingTo("20.05");verifyNoInteractions(polls,candidates);
 }
 @Test void completeIsTheDefaultAndBothSumsAreValidated(){
  assertThatThrownBy(()->service.calculate(request(pair("46","43"),pair("50","50"),null))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.calculate(request(pair("50","50"),pair("47","45"),true))).isInstanceOf(IllegalArgumentException.class);
 }
 @ParameterizedTest @ValueSource(strings={"-1","101"}) void rejectsOutOfRangeValues(String value){assertThatThrownBy(()->service.calculate(request(pair(value,"0"),pair("47","45"),false))).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsDuplicateMissingAndMismatchedCandidates(){
  var duplicate=List.of(new ImportModels.CandidateResult(22,BigDecimal.ONE),new ImportModels.CandidateResult(22,BigDecimal.ONE));
  var mismatch=List.of(new ImportModels.CandidateResult(22,BigDecimal.ONE),new ImportModels.CandidateResult(99,BigDecimal.ONE));
  for(var invalid:List.of(duplicate,mismatch,List.of(new ImportModels.CandidateResult(22,BigDecimal.ONE))))assertThatThrownBy(()->service.calculate(request(invalid,pair("47","45"),false))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.calculate(request(null,pair("47","45"),false))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rejectsImpossiblePartialSum(){assertThatThrownBy(()->service.calculate(request(pair("80","30"),pair("47","45"),false))).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectsMixedModesAndNonFinalOfficialData(){
  var p=pair("50","50");
  assertThatThrownBy(()->service.calculate(new PerformanceCalculationService.Request(UUID.randomUUID(),null,22,13,true,null,p,p,true))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.calculate(new PerformanceCalculationService.Request(null,null,22,13,true,p,p,p,true))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service.calculate(new PerformanceCalculationService.Request(null,null,22,13,false,null,p,p,true))).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(polls,candidates);
 }
}
