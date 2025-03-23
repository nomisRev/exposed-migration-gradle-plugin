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
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Gradle API
    implementation(gradleApi())

    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:0.60.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.60.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.60.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.60.0")
    implementation("org.jetbrains.exposed:exposed-migration:0.60.0")


    // Flyway
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-gradle-plugin:10.10.0")
    implementation("org.postgresql:postgresql:42.6.0")

    // TestContainers
    implementation("org.testcontainers:testcontainers:1.19.7")
    implementation("org.testcontainers:postgresql:1.19.7")
    implementation("org.testcontainers:mysql:1.19.7")
    implementation("org.testcontainers:mariadb:1.19.7")
    implementation("org.testcontainers:mssqlserver:1.19.7")
    implementation("org.testcontainers:oracle-xe:1.19.7")
    implementation("org.testcontainers:jdbc:1.19.7")

    // Database drivers
    implementation("com.h2database:h2:2.2.224")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.6.0.jre11")
    implementation("com.oracle.database.jdbc:ojdbc11:23.3.0.23.09")
    implementation("org.xerial:sqlite-jdbc:3.45.2.0")

    // Testing
    testImplementation(kotlin("test"))
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
