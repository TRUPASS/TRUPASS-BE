package com.trupass.core.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Trupass 사용자. 지금은 Google 계정으로만 가입한다({@code googleSub}가 Google 계정 고유 ID). */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(nullable = false, length = 320)
  private String email;

  private String name;

  @Column(name = "picture_url", length = 2048)
  private String pictureUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_login_at", nullable = false)
  private Instant lastLoginAt;

  protected User() {}

  private User(String googleSub, String email, String name, String pictureUrl, Instant now) {
    this.googleSub = googleSub;
    this.email = email;
    this.name = name;
    this.pictureUrl = pictureUrl;
    this.createdAt = now;
    this.updatedAt = now;
    this.lastLoginAt = now;
  }

  public static User fromGoogle(
      String googleSub, String email, String name, String pictureUrl, Instant now) {
    return new User(googleSub, email, name, pictureUrl, now);
  }

  /** 로그인할 때마다 Google 프로필(이메일·이름·사진)을 최신으로 맞춘다. */
  public void recordGoogleLogin(String email, String name, String pictureUrl, Instant now) {
    this.email = email;
    this.name = name;
    this.pictureUrl = pictureUrl;
    this.updatedAt = now;
    this.lastLoginAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPictureUrl() {
    return pictureUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
