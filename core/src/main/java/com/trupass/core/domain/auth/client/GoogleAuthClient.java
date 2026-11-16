package com.trupass.core.domain.auth.client;

import com.trupass.core.domain.auth.client.dto.GoogleAuthClientResDTO;

/** Google ID 토큰 검증. 구현체는 {@code global/config/ClientConfig}에서 연결한다. */
public interface GoogleAuthClient {

  /**
   * FE가 Google Identity Services로 받은 ID 토큰(JWT)의 서명·발급자·대상(client ID)·만료를 검증하고 계정 정보를 돌려준다.
   *
   * @throws com.trupass.core.global.apiPayload.exception.GeneralException 토큰이 틀리면 {@code
   *     AUTH401_1}, Google 서버에 닿지 못하면 {@code AUTH502_1}
   */
  GoogleAuthClientResDTO.IdToken verifyIdToken(String idToken);
}
