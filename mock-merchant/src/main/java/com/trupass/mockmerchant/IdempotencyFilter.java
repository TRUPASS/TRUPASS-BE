package com.trupass.mockmerchant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * {@code Idempotency-Key} 헤더가 있는 POST 요청은 첫 응답을 저장해 두고, 같은 키로 다시 오면 그 응답을 그대로 돌려준다.
 *
 * <ul>
 *   <li>같은 키 + 같은 요청(메서드·경로·본문) → 저장된 상태 코드와 본문 재생, {@code Idempotent-Replayed: true} 헤더
 *   <li>같은 키 + 다른 요청 → 422 {@code IDEMPOTENCY_KEY_REUSED}
 *   <li>같은 키의 첫 요청이 아직 처리 중 → 409 {@code IDEMPOTENCY_IN_PROGRESS}
 *   <li>2xx·4xx는 저장한다(예: SOLD_OUT 409도 재생된다). 5xx·타임아웃은 저장하지 않아 같은 키로 재시도할 수 있다.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {
  public static final String HEADER = "Idempotency-Key";
  public static final String REPLAYED_HEADER = "Idempotent-Replayed";

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String key = request.getHeader(HEADER);
    return !"POST".equals(request.getMethod())
        || key == null
        || key.isBlank()
        || !FailureInjectionFilter.isMerchantPath(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = request.getHeader(HEADER).trim();
    CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
    String fingerprint =
        fingerprint(request.getMethod(), request.getRequestURI(), cachedRequest.body);

    Entry existing = entries.putIfAbsent(key, new InFlight(fingerprint));
    if (existing == null) {
      process(key, fingerprint, cachedRequest, response, filterChain);
    } else if (!existing.fingerprint().equals(fingerprint)) {
      JsonErrors.write(
          response,
          HttpStatus.UNPROCESSABLE_CONTENT,
          "IDEMPOTENCY_KEY_REUSED",
          "key used with a different request");
    } else if (existing instanceof Completed completed) {
      replay(completed, response);
    } else {
      JsonErrors.write(
          response,
          HttpStatus.CONFLICT,
          "IDEMPOTENCY_IN_PROGRESS",
          "request with this key is in progress");
    }
  }

  private void process(
      String key,
      String fingerprint,
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, wrapper);
    } catch (ServletException | IOException | RuntimeException e) {
      entries.remove(key);
      throw e;
    }
    if (wrapper.getStatus() >= 500) {
      entries.remove(key);
    } else {
      entries.put(
          key,
          new Completed(
              fingerprint,
              wrapper.getStatus(),
              wrapper.getContentType(),
              wrapper.getContentAsByteArray()));
    }
    wrapper.copyBodyToResponse();
  }

  private static void replay(Completed entry, HttpServletResponse response) throws IOException {
    response.setStatus(entry.status());
    if (entry.contentType() != null) {
      response.setContentType(entry.contentType());
    }
    response.setHeader(REPLAYED_HEADER, "true");
    response.getOutputStream().write(entry.body());
  }

  private static String fingerprint(String method, String uri, byte[] body) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update((method + " " + uri + "\n").getBytes(StandardCharsets.UTF_8));
      digest.update(body);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private sealed interface Entry permits InFlight, Completed {
    String fingerprint();
  }

  private record InFlight(String fingerprint) implements Entry {}

  private record Completed(String fingerprint, int status, String contentType, byte[] body)
      implements Entry {}

  /** 본문을 한 번 읽어 지문을 만든 뒤에도 컨트롤러가 다시 읽을 수 있게 메모리에 담아 둔다. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request) throws IOException {
      super(request);
      this.body = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream source = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return source.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return source.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return source.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
