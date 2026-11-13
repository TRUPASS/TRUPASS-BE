package com.trupass.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trupass.core.domain.mandate.client.AuditClient;
import com.trupass.core.domain.merchant.client.MerchantClient;
import com.trupass.core.domain.payment.client.PaymentClient;
import com.trupass.core.domain.trip.client.AgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TrupassApplicationTest {

  /** infra/docker-compose.yml과 같은 이미지를 쓴다. */
  static final String POSTGRES_IMAGE = "pgvector/pgvector:0.8.7-pg18";

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer(
          DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));

  @Autowired ApplicationContext context;

  @Autowired MockMvc mockMvc;

  @Test
  void 컨텍스트가_뜨고_client마다_구현체가_하나씩_연결된다() {
    assertThat(context.getBean(MerchantClient.class)).isNotNull();
    assertThat(context.getBean(PaymentClient.class)).isNotNull();
    assertThat(context.getBean(AuditClient.class)).isNotNull();
    assertThat(context.getBean(AgentClient.class)).isNotNull();
  }

  @Test
  void 헬스체크와_OpenAPI_문서가_열린다() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void FE_개발_서버_origin에_CORS가_열려_있다() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs").header("Origin", "http://localhost:5173"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void 검증_실패는_공통_응답_형식의_400으로_내려간다() throws Exception {
    mockMvc
        .perform(
            post("/internal/tools/search-offers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destination\":\"\",\"travelers\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value("COMMON4001"))
        .andExpect(jsonPath("$.result.destination").exists());
  }

  @Test
  void 구현_전인_client는_501로_알린다() throws Exception {
    mockMvc
        .perform(
            post("/internal/tools/search-offers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"destination":"Osaka","startDate":"2026-12-01","endDate":"2026-12-03","travelers":2}
                    """))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value("COMMON501"));
  }

  @Test
  void 없는_경로는_공통_응답_형식의_404로_내려간다() throws Exception {
    mockMvc
        .perform(get("/no-such-path"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COMMON404"));
  }
}
