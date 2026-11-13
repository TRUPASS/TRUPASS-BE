package com.trupass.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * docs/architecture.md "규칙" 1~3번을 빌드에서 강제한다. 이 테스트가 깨지면 {@code ./gradlew build}가 실패한다.
 *
 * <p>4~6번(남의 repository 금지, 멱등키, 트랜잭션 안 외부 호출 금지)은 코드 리뷰로 지킨다.
 */
@AnalyzeClasses(packages = "com.trupass.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String CLIENT_IMPL = "com.trupass.core.domain.*.client.*..";
  private static final String CLIENT_DTO = "com.trupass.core.domain.*.client.dto..";

  /** 규칙 1. controller는 service만 부른다. */
  @ArchTest
  static final ArchRule controllerCallsOnlyService =
      noClasses()
          .that()
          .resideInAPackage("..domain.*.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..domain.*.repository..", "..domain.*.client..")
          .because("controller는 service만 부른다. repository와 client는 service를 거친다");

  /** 규칙 2. 외부 API는 client/로만 부른다. service 등에서 RestClient를 직접 쓰지 않는다. */
  @ArchTest
  static final ArchRule onlyClientsUseHttpClients =
      noClasses()
          .that()
          .resideOutsideOfPackages(CLIENT_IMPL, "com.trupass.core.global..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(RestClient.class, RestTemplate.class)
          .because("외부 API는 client/ 인터페이스의 구현체 안에서만 부른다");

  /** 규칙 3. client 구현체는 global/config에서만 연결한다. */
  @ArchTest
  static final ArchRule clientImplementationsAreWiredOnlyInConfig =
      classes()
          .that()
          .resideInAPackage(CLIENT_IMPL)
          .and()
          .resideOutsideOfPackage(CLIENT_DTO)
          .should()
          .onlyBeAccessed()
          .byClassesThat()
          .resideInAnyPackage(CLIENT_IMPL, "com.trupass.core.global.config..")
          .because("service는 client 인터페이스만 알고, 구현체(Mock/Toss/Duffel 등)는 설정으로 바꾼다");
}
