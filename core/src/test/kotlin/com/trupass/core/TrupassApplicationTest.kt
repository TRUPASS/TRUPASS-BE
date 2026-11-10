package com.trupass.core

import com.trupass.core.application.port.AuditPort
import com.trupass.core.application.port.MerchantPort
import com.trupass.core.application.port.PaymentPort
import com.trupass.core.application.port.SagaStatePort
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TrupassApplicationTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `컨텍스트가 뜨고 포트마다 어댑터가 하나씩 연결된다`() {
        assertNotNull(context.getBean(MerchantPort::class.java))
        assertNotNull(context.getBean(PaymentPort::class.java))
        assertNotNull(context.getBean(AuditPort::class.java))
        assertNotNull(context.getBean(SagaStatePort::class.java))
    }

    @Test
    fun `헬스체크와 OpenAPI 문서가 열린다`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
        mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }
    }

    @Test
    fun `FE 개발 서버 origin에 CORS가 열려 있다`() {
        mockMvc
            .get("/v3/api-docs") {
                header("Origin", "http://localhost:5173")
            }.andExpect {
                header { string("Access-Control-Allow-Origin", "http://localhost:5173") }
            }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer(
                DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"),
            )
    }
}

/** infra/docker-compose.yml과 같은 이미지를 쓴다. */
const val POSTGRES_IMAGE = "pgvector/pgvector:0.8.7-pg18"
