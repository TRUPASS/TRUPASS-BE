package com.trupass.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trupass.core.application.port.AuditPort;
import com.trupass.core.application.port.MerchantPort;
import com.trupass.core.application.port.PaymentPort;
import com.trupass.core.application.port.SagaStatePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
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
  void 컨텍스트가_뜨고_포트마다_어댑터가_하나씩_연결된다() {
    assertThat(context.getBean(MerchantPort.class)).isNotNull();
    assertThat(context.getBean(PaymentPort.class)).isNotNull();
    assertThat(context.getBean(AuditPort.class)).isNotNull();
    assertThat(context.getBean(SagaStatePort.class)).isNotNull();
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
}
