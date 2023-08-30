
plugins {
    id("jacoco")
    `java-library`
    kotlin("plugin.spring") version "1.9.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
    kotlin("jvm") version "1.9.10"
}

configurations {
    testImplementation { exclude(group = "org.junit.vintage") }
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.spring.io/milestone")
    }

    maven {
        url = uri("https://repo.spring.io/snapshot")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.0.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.1.0")
    testImplementation("io.projectreactor:reactor-test:3.5.4")
}

group = "com.tabiiki"
version = "0.0.1-SNAPSHOT"
description = "kotlin-lab"

configure<SourceSetContainer> {
    named("main") {
        java.srcDir("src/main/kotlin")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
