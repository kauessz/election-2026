package br.com.election;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import br.com.election.tse.TseResultsClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="election.projection.enabled=false") @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class ProjectionDisabledTests {
 @Autowired MockMvc mvc;@Autowired TseResultsClient client;
 @Test void disabledProjectionDoesNotBreakAnyAnalyticalOrOfficialEndpoint() throws Exception {
  long requests=client.metrics().requests();
  mvc.perform(get("/api/election/president/projection")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.publishable").value(false)).andExpect(jsonPath("$.results").isEmpty());
  mvc.perform(get("/api/election/president/projection/history")).andExpect(status().isOk()).andExpect(content().json("[]"));
  mvc.perform(get("/api/election/president/trend")).andExpect(status().isOk());
  mvc.perform(get("/api/election/president/remaining")).andExpect(status().isOk());
  mvc.perform(get("/api/election/president")).andExpect(status().isOk()).andExpect(jsonPath("$.official").value(true));
  assertThat(client.metrics().requests()).isEqualTo(requests);
 }
}
