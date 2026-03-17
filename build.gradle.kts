plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.redis"
version = "0.0.1-SNAPSHOT"
description = "stock-analysis-agent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "2.0.0-M2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("com.redis:agent-memory-client-java:0.1.0")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-aarch_64"
        }
    }
    runtimeOnly("io.netty:netty-resolver-dns-native-macos") {
        artifact {
            classifier = "osx-x86_64"
        }
    }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
