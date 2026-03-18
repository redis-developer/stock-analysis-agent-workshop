plugins {
    base
}

group = "com.redis"
version = "0.0.1-SNAPSHOT"
description = "stock-analysis-agent"

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

tasks.named("clean") {
    dependsOn(":stockanalysisagent:clean")
    dependsOn(":stockanalysisagentworkshop:clean")
}

tasks.named("build") {
    dependsOn(":stockanalysisagent:build")
    dependsOn(":stockanalysisagentworkshop:build")
}

tasks.register("compileJava") {
    group = "build"
    description = "Compiles the finalized implementation Java sources."
    dependsOn(":stockanalysisagent:compileJava")
}

tasks.register("test") {
    group = "verification"
    description = "Runs the finalized implementation tests."
    dependsOn(":stockanalysisagent:test")
}

tasks.register("bootRun") {
    group = "application"
    description = "Runs the finalized implementation application."
    dependsOn(":stockanalysisagent:bootRun")
}
