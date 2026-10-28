plugins {
    id("org.springframework.boot") version "4.1.2"
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
}

group = "com.nurseflow"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

kotlin {
    jvmToolchain(21)
}
