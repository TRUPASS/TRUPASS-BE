package com.trupass.core.domain.search.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 지원 도시 목록. AI는 도시를 텍스트("오사카", "인천")로 주고, 검색 API는 코드(Duffel {@code OSA})와 영어 이름(LiteAPI {@code
 * Osaka})을 받는다. 그 사이를 잇는다.
 *
 * <p>스파이크용 고정 목록이다. M1에서 {@code city} 테이블로 옮긴다(erd-changes.md 1절).
 */
@Component
public class CityCatalog {

  /**
   * @param code IATA 도시 코드. 공항이 하나뿐인 도시는 공항 코드와 같다.
   * @param nameEn LiteAPI {@code cityName}에 그대로 넣는 이름.
   * @param countryCode ISO 3166-1 alpha-2.
   */
  public record City(String code, String nameKo, String nameEn, String countryCode) {}

  private static final List<Entry> ENTRIES =
      List.of(
          new Entry(new City("SEL", "서울", "Seoul", "KR"), "인천", "김포", "ICN", "GMP"),
          new Entry(new City("PUS", "부산", "Busan", "KR"), "김해"),
          new Entry(new City("CJU", "제주", "Jeju", "KR"), "제주도"),
          new Entry(new City("OSA", "오사카", "Osaka", "JP"), "간사이", "KIX", "ITM"),
          new Entry(new City("TYO", "도쿄", "Tokyo", "JP"), "동경", "나리타", "하네다", "NRT", "HND"),
          new Entry(new City("FUK", "후쿠오카", "Fukuoka", "JP")),
          new Entry(new City("SPK", "삿포로", "Sapporo", "JP"), "CTS", "신치토세"),
          new Entry(new City("NGO", "나고야", "Nagoya", "JP")),
          new Entry(new City("BKK", "방콕", "Bangkok", "TH"), "DMK"),
          new Entry(new City("TPE", "타이베이", "Taipei", "TW"), "타이페이", "대만", "TSA"),
          new Entry(new City("HKG", "홍콩", "Hong Kong", "HK")),
          new Entry(new City("SIN", "싱가포르", "Singapore", "SG"), "싱가폴"),
          new Entry(new City("DAD", "다낭", "Da Nang", "VN"), "Danang"));

  private record Entry(City city, String... extraAliases) {
    Stream<String> aliases() {
      return Stream.concat(
          Stream.of(city.code(), city.nameKo(), city.nameEn()), Stream.of(extraAliases));
    }
  }

  private final Map<String, City> byAlias = new HashMap<>();
  private final Map<String, City> byCode = new HashMap<>();

  public CityCatalog() {
    for (Entry entry : ENTRIES) {
      byCode.put(entry.city().code(), entry.city());
      entry.aliases().forEach(alias -> byAlias.put(normalize(alias), entry.city()));
    }
  }

  public Optional<City> byCode(String code) {
    return Optional.ofNullable(code == null ? null : byCode.get(code.toUpperCase(Locale.ROOT)));
  }

  /**
   * 텍스트로 도시를 찾는다. 순서: 전체가 별칭과 같음 → 단어 하나가 별칭과 같음 → 별칭이 텍스트 안에 들어 있음(가장 긴 별칭). 마지막 단계는 3글자 영문 코드를 쓰지
   * 않는다("osaka"가 아닌 단어 속 "osa" 같은 오검출을 막는다).
   */
  public Optional<City> find(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String normalized = normalize(text);
    City exact = byAlias.get(normalized);
    if (exact != null) {
      return Optional.of(exact);
    }
    for (String token : normalized.split("[\\s,./·()\\-]+")) {
      City city = byAlias.get(token);
      if (city != null) {
        return Optional.of(city);
      }
    }
    String compact = normalized.replace(" ", "");
    return byAlias.entrySet().stream()
        .filter(alias -> !isAsciiCode(alias.getKey()))
        .filter(alias -> compact.contains(alias.getKey().replace(" ", "")))
        .max(Comparator.comparingInt(alias -> alias.getKey().length()))
        .map(Map.Entry::getValue);
  }

  /** 지원 도시 코드 목록(오류 메시지·문서용). */
  public Set<String> codes() {
    return Set.copyOf(byCode.keySet());
  }

  private static boolean isAsciiCode(String alias) {
    return alias.length() <= 3 && alias.chars().allMatch(ch -> ch < 128);
  }

  private static String normalize(String text) {
    return text.strip().toLowerCase(Locale.ROOT);
  }
}
