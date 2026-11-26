package com.trupass.core.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserServiceTest {

  @Test
  void 연령대는_나이를_10살_단위로_내린다() {
    assertThat(UserService.ageGroup(34)).isEqualTo(30);
    assertThat(UserService.ageGroup(30)).isEqualTo(30);
    assertThat(UserService.ageGroup(29)).isEqualTo(20);
    assertThat(UserService.ageGroup(9)).isZero();
    assertThat(UserService.ageGroup(0)).isZero();
    assertThat(UserService.ageGroup(-1)).isZero();
    assertThat(UserService.ageGroup(100)).isEqualTo(100);
  }
}
