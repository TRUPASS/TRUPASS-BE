package com.trupass.mockmerchant

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * `Idempotency-Key` 헤더가 있는 POST 요청은 첫 응답을 저장해 두고, 같은 키로 다시 오면 그 응답을 그대로 돌려준다.
 *
 * - 같은 키 + 같은 요청(메서드·경로·본문) → 저장된 상태 코드와 본문 재생, `Idempotent-Replayed: true` 헤더
 * - 같은 키 + 다른 요청 → 422 `IDEMPOTENCY_KEY_REUSED`
 * - 같은 키의 첫 요청이 아직 처리 중 → 409 `IDEMPOTENCY_IN_PROGRESS`
 * - 2xx·4xx는 저장한다(예: SOLD_OUT 409도 재생된다). 5xx·타임아웃은 저장하지 않아 같은 키로 재시도할 수 있다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class IdempotencyFilter : OncePerRequestFilter() {
    private val entries = ConcurrentHashMap<String, Entry>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" ||
            request.getHeader(HEADER).isNullOrBlank() ||
            FailureInjectionFilter.MERCHANT_PATHS.none { request.requestURI.startsWith(it) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = request.getHeader(HEADER).trim()
        val cachedRequest = CachedBodyRequest(request)
        val fingerprint = fingerprint(request.method, request.requestURI, cachedRequest.body)

        when (val existing = entries.putIfAbsent(key, Entry.InFlight(fingerprint))) {
            null -> {
                process(key, fingerprint, cachedRequest, response, filterChain)
            }

            else -> {
                when {
                    existing.fingerprint != fingerprint -> {
                        writeError(
                            response,
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "IDEMPOTENCY_KEY_REUSED",
                            "key used with a different request",
                        )
                    }

                    existing is Entry.InFlight -> {
                        writeError(response, HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "request with this key is in progress")
                    }

                    existing is Entry.Completed -> {
                        replay(existing, response)
                    }
                }
            }
        }
    }

    private fun process(
        key: String,
        fingerprint: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrapper = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, wrapper)
        } catch (e: Exception) {
            entries.remove(key)
            throw e
        }
        if (wrapper.status >= 500) {
            entries.remove(key)
        } else {
            entries[key] = Entry.Completed(fingerprint, wrapper.status, wrapper.contentType, wrapper.contentAsByteArray)
        }
        wrapper.copyBodyToResponse()
    }

    private fun replay(
        entry: Entry.Completed,
        response: HttpServletResponse,
    ) {
        response.status = entry.status
        entry.contentType?.let { response.contentType = it }
        response.setHeader(REPLAYED_HEADER, "true")
        response.outputStream.write(entry.body)
    }

    private fun writeError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write("""{"code":"$code","message":"$message"}""")
    }

    private fun fingerprint(
        method: String,
        uri: String,
        body: ByteArray,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$method $uri\n".toByteArray())
        digest.update(body)
        return HexFormat.of().formatHex(digest.digest())
    }

    private sealed interface Entry {
        val fingerprint: String

        data class InFlight(
            override val fingerprint: String,
        ) : Entry

        class Completed(
            override val fingerprint: String,
            val status: Int,
            val contentType: String?,
            val body: ByteArray,
        ) : Entry
    }

    /** 본문을 한 번 읽어 지문을 만든 뒤에도 컨트롤러가 다시 읽을 수 있게 메모리에 담아 둔다. */
    private class CachedBodyRequest(
        request: HttpServletRequest,
    ) : HttpServletRequestWrapper(request) {
        val body: ByteArray = request.inputStream.readAllBytes()

        override fun getInputStream(): ServletInputStream {
            val source = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = source.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = source.read(b, off, len)

                override fun isFinished(): Boolean = source.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: ReadListener?): Unit = throw UnsupportedOperationException()
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))
    }

    companion object {
        const val HEADER = "Idempotency-Key"
        const val REPLAYED_HEADER = "Idempotent-Replayed"
    }
}
