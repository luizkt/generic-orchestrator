import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.orchestrator"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly { extendsFrom(configurations.annotationProcessor.get()) }
}

repositories { mavenCentral() }

extra["awsSdkVersion"] = "2.30.0"
extra["jjwtVersion"] = "0.12.6"
extra["testcontainersVersion"] = "1.20.4"
extra["resilience4jVersion"] = "2.2.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Resilience4j (retry + circuit breaker para integrações HTTP)
    implementation("io.github.resilience4j:resilience4j-retry:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:${property("resilience4jVersion")}")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // AWS SQS
    implementation("software.amazon.awssdk:sqs:${property("awsSdkVersion")}")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // Jackson YAML
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testes
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:rabbitmq:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:kafka:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:localstack:${property("testcontainersVersion")}")
    testImplementation("software.amazon.awssdk:sqs:${property("awsSdkVersion")}")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("generic-orchestrator.jar")
}

// JaCoCo: cobertura ≥ 95% nas classes das features feature_3 (resiliência) e
// do refactor para o Manager (workflow client + cache).
val coverageIncludes = listOf(
    // feature_3 — Retry + Circuit Breaker
    "com/orchestrator/integration/http/HttpIntegrationExecutor.class",
    "com/orchestrator/config/HttpResilienceConfig.class",
    "com/orchestrator/config/properties/RetryConfigurationProperties.class",
    "com/orchestrator/config/properties/CircuitBreakerConfigurationProperties.class",
    "com/orchestrator/exception/RetriableHttpException.class",
    // Manager client + Redis cache
    "com/orchestrator/manager/**",
    "com/orchestrator/config/RedisCacheConfig.class",
    "com/orchestrator/config/ManagerWebClientConfig.class",
    "com/orchestrator/config/properties/ManagerProperties.class"
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}
