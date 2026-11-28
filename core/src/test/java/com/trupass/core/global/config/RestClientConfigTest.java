package com.trupass.core.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** 실제 소켓으로 요청을 받아 core가 무엇을 보내는지 본다. */
class RestClientConfigTest {
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void 평문_http로_보낼_때_h2c_업그레이드를_요청하지_않는다() throws Exception {
    // JDK HttpClient 기본값(HTTP/2)은 평문 http에서 Upgrade: h2c를 붙인다.
    // uvicorn(agent)은 이 업그레이드를 거절하면서 요청 본문을 버려서, POST 본문이 비어 도착했다.
    AtomicReference<String> upgrade = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/echo",
        exchange -> {
          upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    RestClient client =
        RestClient.builder()
            .requestFactory(
                RestClientConfig.requestFactory(Duration.ofSeconds(2), Duration.ofSeconds(2)))
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .build();

    client
        .post()
        .uri("/echo")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"a\":1}")
        .retrieve()
        .toBodilessEntity();

    assertThat(upgrade.get()).isNull();
    assertThat(body.get()).isEqualTo("{\"a\":1}");
  }
}
