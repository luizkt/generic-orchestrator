import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
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

extra["awsSdkVersion"] = "2.57.11"
extra["jjwtVersion"] = "0.13.0"
extra["testcontainersVersion"] = "1.21.4"
extra["resilience4jVersion"] = "2.4.0"
extra["sbaVersion"] = "3.5.11"

dependencies {
    implementation("de.codecentric:spring-boot-admin-starter-client:${property("sbaVersion")}")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
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

    // Swagger / OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.10.0")

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

spotless {
    java {
        licenseHeaderFile(file("${projectDir}/license-header.txt"))
        googleJavaFormat("1.19.2").aosp()
        targetExclude("**/build/**", "**/generated/**")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
    // Docker Engine 29+ recusa a API 1.32 que o docker-java do Testcontainers 1.20.x usa por padrão.
    systemProperty("api.version", "1.44")
}

// `test` roda só os unitários; os `*IT` (Testcontainers, precisam de Docker) ficam em `integrationTest`.
tasks.named<Test>("test") {
    exclude("**/it/**")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Testes de integração (Testcontainers) em com.orchestrator.it."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/it/**")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(integrationTest) }

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
    "com/orchestrator/config/properties/ManagerProperties.class",
    // Endpoint admin de invalidação de cache (cross-service)
    "com/orchestrator/controller/CacheAdminController.class",
    // Serviços de domínio (validação de contrato, templates, YAML)
    "com/orchestrator/service/ContractValidationService.class",
    "com/orchestrator/service/TemplateResolverService.class",
    "com/orchestrator/service/YamlParserService.class",
    // Filas (publishers + executor) e configs de cliente
    "com/orchestrator/integration/queue/**",
    "com/orchestrator/config/SqsConfig.class",
    "com/orchestrator/config/KafkaMultiInstanceConfig.class",
    "com/orchestrator/config/RabbitMqMultiInstanceConfig.class",
    // JWT, filtro de MDC e tratamento global de erros
    "com/orchestrator/security/JwtService.class",
    "com/orchestrator/filter/ExecutionMdcFilter.class",
    "com/orchestrator/exception/**"
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"), integrationTest)
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
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
    dependsOn(tasks.named("test"), integrationTest)
    // Junta o exec de todos os testes (no CI, os de jobs paralelos chegam por artifact).
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
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
