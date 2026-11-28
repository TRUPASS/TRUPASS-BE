package com.trupass.core.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trupass.core.domain.search.service.CityCatalog.City;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CityCatalogTest {
  private final CityCatalog cities = new CityCatalog();

  @ParameterizedTest
  @ValueSource(
      strings = {"오사카", "Osaka", "osaka", "OSA", "KIX", "간사이", "오사카 2박", "일본 오사카", "오사카에서"})
  void AI가_준_도시_텍스트로_도시를_찾는다(String text) {
    assertThat(cities.find(text)).map(City::code).hasValue("OSA");
  }

  @Test
  void 공항_이름으로도_도시를_찾는다() {
    assertThat(cities.find("인천")).map(City::code).hasValue("SEL");
    assertThat(cities.find("김포공항")).map(City::code).hasValue("SEL");
    assertThat(cities.find("제주도 여행")).map(City::code).hasValue("CJU");
  }

  @Test
  void 도시에는_검색_API에_넣을_영어_이름과_국가_코드가_있다() {
    City osaka = cities.byCode("osa").orElseThrow();

    assertThat(osaka.nameEn()).isEqualTo("Osaka");
    assertThat(osaka.countryCode()).isEqualTo("JP");
    assertThat(osaka.nameKo()).isEqualTo("오사카");
  }

  @ParameterizedTest
  @ValueSource(strings = {"화성", "", "  "})
  void 모르는_도시는_찾지_않는다(String text) {
    assertThat(cities.find(text)).isEmpty();
  }

  @Test
  void null이면_찾지_않는다() {
    assertThat(cities.find(null)).isEmpty();
  }

  @Test
  void 영문_단어_속_세_글자는_도시_코드로_보지_않는다() {
    // "business"에는 SIN, "mimosa"에는 OSA가 들어 있다.
    assertThat(cities.find("business trip")).isEmpty();
    assertThat(cities.find("mimosa")).isEmpty();
  }

  @Test
  void 지원_도시_코드_목록() {
    assertThat(cities.codes()).hasSize(13).contains("SEL", "OSA", "TYO", "DAD");
  }
}
