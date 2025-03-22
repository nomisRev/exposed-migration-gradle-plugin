# Exposed Migration Gradle Plugin

## Overview

Databases in production always require dealing with schema evolution. This can be caused by changing expectations, or evolving features that involve adding, removing, or changing constraints, etc. in a schema.

Typically, tools like Flyway or Liquibase are used to evolve your database schema easily and reliably. As indicated by `createMissingTablesAndColumns` from the exposed-migration module, the concerns typically are that "Execution of this function might lead to unpredictable state in the database if a failure occurs at any point". Flyway makes sure that you always end up in a predictable state, even if a failure occurs.

This Gradle plugin integrates JetBrains Exposed (version 0.60.0) with Flyway to automatically generate SQL migration scripts from your Exposed table definitions. This eliminates the need to manually write SQL migration scripts, reducing errors and ensuring consistency between your Exposed schema and database schema.

## How It Works

Flyway and similar tools need an ordered set of migration `.sql` scripts that can be run in sequence to reach the current state of the evolved schema. These are versioned and represent the order in which the migrations need to run.

Let's take an example of an empty database, and we define a table:

```kotlin
object User : LongIdTable("users", "user_id") {
    val name = varchar("name", 100)
}
```

This would generate a file at `resources/db/migration/V1_0__user_table.sql` with the content:

```sql
CREATE TABLE IF NOT EXISTS users (
  user_id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);
```

This is V1 of our database, or our baseline. When we need to make a change to our Users table, we need to evolve our schema:

```kotlin
object User : LongIdTable("users", "user_id") {
    val name = varchar("name", 100)
    val email = varchar("email", 100)
}
```

This would generate a file at `resources/db/migration/V1_1__email_column.sql` with the content:

```sql
ALTER TABLE users ADD email VARCHAR(100);
```

This allows you to automatically generate migration scripts based on your Exposed tables, ensuring they're always aligned. Manually writing these files is error-prone because slight differences might exist between the SQL from Exposed users and the SQL Exposed generates under the hood, resulting in strange bugs.

This also eliminates the need to maintain duplicate schema definitions (one in Exposed and another in SQL), which defeats the purpose of having an ORM to avoid writing SQL altogether.

## Setup

### Gradle Plugin

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.exposed-migration") version "1.0-SNAPSHOT"
}
```

### Configuration

Configure the plugin in your `build.gradle.kts`:

```kotlin
exposedMigration {
    // Directory containing your Exposed table definitions
    tablesPackage = "com.example.db.tables"

    // Output directory for generated migration scripts (relative to project root)
    outputDirectory = "src/main/resources/db/migration"

    // Optional: Prefix for migration version numbers (default: "V")
    versionPrefix = "V"

    // Optional: Separator between version and description (default: "__")
    separator = "__"

    // Optional: Starting version number (default: "1_0")
    baselineVersion = "1_0"
}
```

## Usage

The plugin adds the following tasks to your Gradle build:

```bash
# Generate migration files based on your Exposed table definitions
./gradlew generateMigrationFiles

# Run Flyway migrations to update your database schema
./gradlew flywayMigrate
```

You can also integrate these tasks into your build process:

```kotlin
tasks.build {
    dependsOn("generateMigrationFiles")
}
```

## Flyway Integration

The plugin automatically configures Flyway with sensible defaults:

- Migration scripts location: `src/main/resources/db/migration`
- Migration file naming pattern: `V<version>__<description>.sql`
- Database connection details are read from your application configuration

You can customize Flyway configuration in your `build.gradle.kts`:

```kotlin
flyway {
    url = "jdbc:postgresql://localhost:5432/mydb"
    user = "postgres"
    password = "password"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    baselineOnMigrate = true
}
```

## Testing

The plugin includes comprehensive testing to ensure reliability:

1. **Unit Tests**: Test individual components of the migration generation system
2. **Integration Tests**: Test the interaction between Exposed schema definitions and generated SQL
3. **Functional Tests**: Test the Gradle plugin tasks and Flyway integration with various database types

Run the tests with:

```bash
./gradlew test
```

## Expected Challenges

- Users might opt-in to the system on existing infrastructure, and we need to take into account their existing schema.
- Ideally, readable and meaningful names need to be generated for the files, based on changes in the table(s).
- Support for different tools like Flyway and Liquibase (and maybe more) might require different files to be generated.
- More fine-grained Ktor integration in regards to migrations might be desired (Flyway plugin?).

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details.
