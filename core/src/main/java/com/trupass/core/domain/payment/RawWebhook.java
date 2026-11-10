package com.trupass.core.domain.payment;

import java.util.List;
import java.util.Map;

/** PG 웹훅 원문. 서명 검증은 어댑터가 하므로 본문 바이트를 손대지 않고 그대로 넘긴다. */
public record RawWebhook(Map<String, List<String>> headers, byte[] body) {
  public RawWebhook {
    headers = Map.copyOf(headers);
    body = body.clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }
}
