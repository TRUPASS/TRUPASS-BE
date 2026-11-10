package com.trupass.core.domain.mandate;

/** 감사 저장소에 기록된 위치와 무결성 해시. */
public record AuditRef(String location, String sha256) {}
