plugins {
    kotlin("jvm") version "2.1.10"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "org.jetbrains.exposed"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(gradleApi())

    implementation("org.jetbrains.exposed:exposed-core:0.60.0")
    implementation("org.jetbrains.exposed:exposed-migration:0.60.0")


    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

gradlePlugin {
    plugins {
        create("exposedMigrationPlugin") {
            id = "org.jetbrains.exposed.migration"
            implementationClass = "org.jetbrains.exposed.migration.plugin.ExposedMigrationPlugin"
            displayName = "Exposed Migration Plugin"
            description = "Gradle plugin for generating SQL migration scripts from Exposed table definitions"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

kotlin {
    jvmToolchain(21)
}
