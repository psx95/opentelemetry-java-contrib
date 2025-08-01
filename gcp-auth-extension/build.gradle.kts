plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("com.github.johnrengelman.shadow")
  id("org.springframework.boot") version "2.7.18"
}

description = "OpenTelemetry extension that provides GCP authentication support for OTLP exporters"
otelJava.moduleName.set("io.opentelemetry.contrib.gcp.auth")

val agent: Configuration by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  annotationProcessor("com.google.auto.service:auto-service")
  // We use `compileOnly` dependency because during runtime all necessary classes are provided by
  // javaagent itself.
  compileOnly("com.google.auto.service:auto-service-annotations")
  compileOnly("io.opentelemetry:opentelemetry-api")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")

  // Only dependencies added to `implementation` configuration will be picked up by Shadow plugin
  implementation("com.google.auth:google-auth-library-oauth2-http:1.37.1")

  // Test dependencies
  testCompileOnly("com.google.auto.service:auto-service-annotations")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testCompileOnly("org.junit.jupiter:junit-jupiter-params")

  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations")

  testImplementation("org.awaitility:awaitility")
  testImplementation("org.mockito:mockito-inline")
  testImplementation("org.mockito:mockito-junit-jupiter")
  testImplementation("org.mock-server:mockserver-netty:5.15.0")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.7.0-alpha")
  testImplementation("org.springframework.boot:spring-boot-starter-web:2.7.18")
  testImplementation("org.springframework.boot:spring-boot-starter:2.7.18")
  testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.18")

  testAnnotationProcessor("com.google.auto.value:auto-value")
  testCompileOnly("com.google.auto.value:auto-value-annotations")

  agent("io.opentelemetry.javaagent:opentelemetry-javaagent")
}

tasks {
  test {
    useJUnitPlatform()
    // Unset relevant environment variables to provide a clean state for the tests
    environment("GOOGLE_CLOUD_PROJECT", "")
    environment("GOOGLE_CLOUD_QUOTA_PROJECT", "")
    // exclude integration test
    exclude("io/opentelemetry/contrib/gcp/auth/GcpAuthExtensionEndToEndTest.class")
  }

  shadowJar {
    /**
     * Shaded version of this extension is required when using it as a OpenTelemetry Java Agent
     * extension. Shading bundles the dependencies required by this extension in the resulting JAR,
     * ensuring their presence on the classpath at runtime.
     *
     * See http://gradleup.com/shadow/introduction/#introduction for reference.
     */
    archiveClassifier.set("shadow")
  }

  jar {
    /**
     * We need to publish both - shaded and unshaded variants of the dependency
     * Shaded dependency is required for use with the Java agent.
     * Unshaded dependency can be used with OTel Autoconfigure module.
     *
     * Not overriding the classifier to empty results in an implicit classifier 'plain' being
     * used with the standard JAR.
     */
    archiveClassifier.set("")
  }

  assemble {
    dependsOn(shadowJar)
  }

  bootJar {
    // disable bootJar in build since it only runs as part of test
    enabled = false
  }
}

val builtLibsDir = layout.buildDirectory.dir("libs").get().asFile.absolutePath
val javaAgentJarPath = "$builtLibsDir/otel-agent.jar"
val authExtensionJarPath = "${tasks.shadowJar.get().archiveFile.get()}"

tasks.register<Copy>("copyAgent") {
  into(layout.buildDirectory.dir("libs"))
  from(configurations.named("agent") {
    rename("opentelemetry-javaagent(.*).jar", "otel-agent.jar")
  })
}

tasks.register<Test>("IntegrationTestUserCreds") {
  dependsOn(tasks.shadowJar)
  dependsOn(tasks.named("copyAgent"))

  useJUnitPlatform()
  // include only the integration test file
  include("io/opentelemetry/contrib/gcp/auth/GcpAuthExtensionEndToEndTest.class")

  val fakeCredsFilePath = project.file("src/test/resources/fake_user_creds.json").absolutePath

  environment("GOOGLE_CLOUD_QUOTA_PROJECT", "quota-project-id")
  environment("GOOGLE_APPLICATION_CREDENTIALS", fakeCredsFilePath)
  jvmArgs = listOf(
    "-javaagent:$javaAgentJarPath",
    "-Dotel.javaagent.extensions=$authExtensionJarPath",
    "-Dgoogle.cloud.project=my-gcp-project",
    "-Dotel.java.global-autoconfigure.enabled=true",
    "-Dotel.exporter.otlp.endpoint=http://localhost:4318",
    "-Dotel.resource.providers.gcp.enabled=true",
    "-Dotel.traces.exporter=otlp",
    "-Dotel.bsp.schedule.delay=2000",
    "-Dotel.metrics.exporter=none",
    "-Dotel.logs.exporter=none",
    "-Dotel.exporter.otlp.protocol=http/protobuf",
    "-Dotel.javaagent.debug=false",
    "-Dmockserver.logLevel=trace"
  )
}
