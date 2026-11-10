package com.trupass.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

/**
 * `trupass.*` 설정. 환경 변수로도 덮어쓸 수 있다(예: `TRUPASS_ADAPTERS_MERCHANT=duffel`).
 * 어댑터 값은 enum이라 오타가 있으면 애플리케이션이 뜨지 않는다.
 */
@ConfigurationProperties("trupass")
data class TrupassProperties(
    val adapters: Adapters = Adapters(),
    val services: Services = Services(),
    val http: Http = Http(),
    val cors: Cors = Cors(),
    val audit: Audit = Audit(),
) {
    data class Adapters(
        val merchant: MerchantAdapter = MerchantAdapter.MOCK,
        val payment: PaymentAdapter = PaymentAdapter.MOCK,
        val audit: AuditAdapter = AuditAdapter.LOCALFILE,
    )

    enum class MerchantAdapter { MOCK, DUFFEL }

    enum class PaymentAdapter { MOCK, TOSS }

    enum class AuditAdapter { LOCALFILE, S3 }

    data class Services(
        val mockMerchantBaseUrl: String = "http://localhost:9001",
        val agentBaseUrl: String = "http://localhost:8001",
    )

    /** 외부 HTTP 호출 공통 타임아웃. */
    data class Http(
        val connectTimeout: Duration = Duration.ofSeconds(2),
        val readTimeout: Duration = Duration.ofSeconds(5),
    )

    data class Cors(
        val allowedOrigins: List<String> = listOf("http://localhost:5173"),
    )

    data class Audit(
        val localDir: Path = Path.of("./data/audit"),
    )
}
