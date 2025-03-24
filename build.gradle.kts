plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.java.gradle.plugin)
    alias(libs.plugins.maven.publish)
}

group = "org.jetbrains.exposed"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    implementation(gradleApi())

    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration)

    implementation(libs.flyway.postgresql)
    implementation(libs.flyway.mysql)
    implementation(libs.flyway.sqlserver)
    implementation(libs.flyway.oracle)

    implementation(libs.testcontainers.postgresql)
    implementation(libs.testcontainers.mysql)
    implementation(libs.testcontainers.mariadb)
    implementation(libs.testcontainers.mssqlserver)
    implementation(libs.testcontainers.oracle)

    // Database drivers
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.mysql)
    implementation(libs.mariadb)
    implementation(libs.mssql)
    implementation(libs.oracle)

    // Testing
    testImplementation(libs.kotlin.test)
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
