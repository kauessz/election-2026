package br.com.election;

import br.com.election.poll.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PollNormalizationServiceTests {
    private final PollNormalizationService service = new PollNormalizationService();
    private List<BigDecimal> values(String... values) { return java.util.Arrays.stream(values).map(BigDecimal::new).toList(); }
    @Test void normalizesTotal() {
        assertThat(service.normalize(VoteBasis.TOTAL, values("47", "45"))).containsExactlyElementsOf(values("51.0870", "48.9130"));
    }
    @Test void preservesValid() {
        assertThat(service.normalize(VoteBasis.VALID, values("51.1", "48.9"))).containsExactlyElementsOf(values("51.1000", "48.9000"));
    }
    @Test void preservesPublishedValidRounding() {
        assertThat(service.normalize(VoteBasis.VALID, values("50.1", "50"))).containsExactlyElementsOf(values("50.1000", "50.0000"));
    }
    @Test void distributesRoundingResidue() {
        var result = service.normalize(VoteBasis.TOTAL, values("1", "1", "1", "0"));
        assertThat(result.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(result.getLast()).isEqualByComparingTo("0");
    }
    @Test void rejectsZeroTotal() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, values("0", "0"))); }
    @Test void rejectsZeroValid() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.VALID, values("0", "0"))); }
    @Test void rejectsEmptyResults() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, List.of())); }
    @Test void rejectsNegative() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, values("-1", "50"))); }
    @Test void rejectsOverHundred() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, values("101"))); }
    @Test void rejectsCombinedOverHundred() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, values("70", "50"))); }
    @Test void rejectsExcessPrecision() { assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(VoteBasis.TOTAL, values("45.12345", "40"))); }
}
