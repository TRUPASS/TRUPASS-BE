import com.diffplug.gradle.spotless.SpotlessExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless)
}

val javaVersion =
    libs.versions.java
        .get()
        .toInt()
val ktlintVersion = libs.versions.ktlint.get()

allprojects {
    group = "com.trupass"
    version = "0.0.1-SNAPSHOT"
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(javaVersion)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(ktlintVersion)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // `./gradlew build`가 포맷 위반도 잡도록 check에 spotlessCheck가 붙어 있다(Spotless 기본 동작).
}
