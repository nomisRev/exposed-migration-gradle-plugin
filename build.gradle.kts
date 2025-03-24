plugins {
    kotlin("jvm") version "2.1.20"
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

    implementation("org.jetbrains.exposed:exposed-jdbc:0.60.0")
    implementation("org.jetbrains.exposed:exposed-migration:0.60.0")

    implementation("org.flywaydb:flyway-database-postgresql:11.4.1")
    implementation("org.flywaydb:flyway-mysql:11.4.1")
    implementation("org.flywaydb:flyway-sqlserver:11.4.1")
    implementation("org.flywaydb:flyway-database-oracle:11.4.1")

    val testContainersVersion = "1.20.6"

    implementation("org.testcontainers:postgresql:$testContainersVersion")
    implementation("org.testcontainers:mysql:$testContainersVersion")
    implementation("org.testcontainers:mariadb:$testContainersVersion")
    implementation("org.testcontainers:mssqlserver:$testContainersVersion")
    implementation("org.testcontainers:oracle-xe:$testContainersVersion")

    // Database drivers
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.10.0.jre11")
    implementation("com.oracle.database.jdbc:ojdbc11:23.7.0.25.01")

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
