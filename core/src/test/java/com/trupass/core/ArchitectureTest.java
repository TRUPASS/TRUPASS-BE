package com.trupass.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 포트와 어댑터 경계를 빌드에서 강제한다. 이 테스트가 깨지면 {@code ./gradlew build}가 실패한다. */
@AnalyzeClasses(packages = "com.trupass.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** 도메인은 JDK 외에는 아무것도 모른다(Spring, JPA, Jackson, 외부 SDK 금지). */
  @ArchTest
  static final ArchRule domainDependsOnlyOnJdk =
      classes()
          .that()
          .resideInAPackage("com.trupass.core.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("com.trupass.core.domain..", "java..")
          .because("도메인 코어는 어떤 외부 SDK나 어댑터에도 의존하지 않는다");

  @ArchTest
  static final ArchRule domainDoesNotDependOnOuterLayers =
      noClasses()
          .that()
          .resideInAPackage("com.trupass.core.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.trupass.core.application..",
              "com.trupass.core.adapter..",
              "com.trupass.core.config..");

  @ArchTest
  static final ArchRule applicationDoesNotDependOnAdaptersOrConfig =
      noClasses()
          .that()
          .resideInAPackage("com.trupass.core.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.trupass.core.adapter..", "com.trupass.core.config..")
          .because("유스케이스는 포트(인터페이스)만 보고, 어떤 어댑터가 꽂혔는지 몰라야 한다");

  @ArchTest
  static final ArchRule inboundAdaptersDoNotCallOutboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("com.trupass.core.adapter.inbound..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.trupass.core.adapter.outbound..")
          .because("컨트롤러는 유스케이스/포트를 거쳐서만 외부와 통신한다");

  @ArchTest
  static final ArchRule outboundAdaptersDoNotCallInboundAdapters =
      noClasses()
          .that()
          .resideInAPackage("com.trupass.core.adapter.outbound..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.trupass.core.adapter.inbound..");
}
