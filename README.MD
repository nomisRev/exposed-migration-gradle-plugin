# Exposed Migration Gradle Plugin

## Overview

Databases in production always require dealing with schema evolution. This can be caused by changing expectations, or
evolving features that involve adding, removing, or changing constraints, etc. in a schema.

Typically, tools like Flyway or Liquibase are used to evolve your database schema easily and reliably. As indicated by
`createMissingTablesAndColumns` from the exposed-migration module, the concerns typically are that "Execution of this
function might lead to unpredictable state in the database if a failure occurs at any point". Flyway makes sure that you
always end up in a predictable state, even if a failure occurs.

This Gradle plugin integrates JetBrains Exposed (version 0.60.0) to automatically generate SQL migration scripts from
your Exposed table definitions. This eliminates the need to manually write SQL migration scripts, reducing errors and
ensuring consistency between your Exposed schema and database schema.

## How It Works

Flyway and similar tools need an ordered set of migration `.sql` scripts that can be run in sequence to reach the
current state of the evolved schema. These are versioned and represent the order in which the migrations need to run.

Let's take an example of an empty database, and we define a table:

```kotlin
object User : LongIdTable("users", "user_id") {
    val name = varchar("name", 100)
}
```

This would generate a file at `resources/db/migration/V1_0__user_table.sql` with the content:

```sql
CREATE TABLE IF NOT EXISTS users
(
    user_id BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL
);
```

This is V1 of our database, or our baseline. When we need to make a change to our Users table, we need to evolve our
schema:

```kotlin
object User : LongIdTable("users", "user_id") {
    val name = varchar("name", 100)
    val email = varchar("email", 100)
}
```

This would generate a file at `resources/db/migration/V1_1__email_column.sql` with the content:

```sql
ALTER TABLE users
    ADD email VARCHAR(100);
```

This allows you to automatically generate migration scripts based on your Exposed tables, ensuring they're always
aligned. Manually writing these files is error-prone because slight differences might exist between the SQL from Exposed
users and the SQL Exposed generates under the hood, resulting in strange bugs.

This also eliminates the need to maintain duplicate schema definitions (one in Exposed and another in SQL), which
defeats the purpose of having an ORM to avoid writing SQL altogether.

## Setup

### Gradle Plugin

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.exposed.migration") version "0.1.0-SNAPSHOT"
}
```

### Configuration

Configure the plugin in your `build.gradle.kts`. You need to either configure the `database`, or `testcontainers`.

#### Database

It will connect to the configured database, and generate a migration script from the current schema to the schema
defined in code by Exposed.

```kotlin
exposedMigration {
    exposedTablesPackage.set("com.example.db.tables")
    databaseUrl.set("jdbc:postgresql://localhost:5432/mydb")
    databaseUser.set("postgres")
    databasePassword.set("password")
}
```

#### TestContainers

The plugin supports the following database container images:

- PostgreSQL: `postgres:latest`
- MySQL: `mysql:latest`
- MariaDB: `mariadb:latest`
- MS SQL Server: `mcr.microsoft.com/mssql/server:latest` or `sqlserver:latest`
- Oracle: `oracle:latest` or `gvenzl/oracle-xe:latest`
-

### Flyway Integration

The plugin automatically uses Flyway to apply existing migration scripts before generating new ones when using
TestContainers. This ensures that new migrations are generated based on the latest database schema, including any
changes made by previous migrations.

When using TestContainers, the plugin will:

1. Start a database container
2. Apply all existing migration scripts in the migrations directory using Flyway
3. Generate new migration scripts based on the current state of the database and your Exposed table definitions
4. Shut down the container

This approach ensures that your migrations are always generated based on the latest database schema, even when working
in a development environment without a persistent database.

```kotlin
exposedMigration {
    exposedTablesPackage.set("com.example.db.tables")
    databaseUrl.set("jdbc:postgresql://localhost:5432/mydb")
    databaseUser.set("postgres")
    databasePassword.set("password")
}
```

#### Full Configuration Example

```kotlin
exposedMigration {
    // Directory where the generated migration scripts will be stored
    migrationsDir.set(layout.projectDirectory.dir("src/main/resources/db/migration"))

    // Optional: Prefix for migration file names (default: "V")
    migrationFilePrefix.set("V")

    // Optional: Separator for migration file names (default: "__")
    migrationFileSeparator.set("__")

    // Optional: File extension for migration files (default: ".sql")
    migrationFileExtension.set(".sql")

    // Optional: Classpath that is scanned for Exposed Tables
    // By default, this is set to the project's runtime classpath
    classpath = sourceSets.main.get().runtimeClasspath
}
```

## Usage

The plugin adds the following tasks to your Gradle build:

```bash
# Generate migration files based on your Exposed table definitions
./gradlew generateMigrations
```

You can also integrate these tasks into your build process:

```kotlin
// Generate migrations before the build task
tasks.named("build") {
    dependsOn("generateMigrations")
}

// Generate migrations before the processResources task
tasks.named("processResources") {
    dependsOn("generateMigrations")
}
```

The plugin generates migration scripts that follow Flyway's naming convention:

- Migration scripts location: Configured by `migrationsDir` in the plugin configuration
- Migration file naming pattern: `<prefix><version><separator><description>.<extension>`

The plugin supports two version formats:

1. `VX__description.sql` (e.g., `V1__create_users_table.sql`)
2. `VX_Y__description.sql` (e.g., `V1_0__create_users_table.sql`)

The format used depends on the existing migration files in your project. If your project already has migration files in
the `VX_Y__` format, the plugin will continue using that format. Otherwise, it will use the `VX__` format.

## File Naming

The plugin automatically generates meaningful file names for migration scripts based on the SQL statements they contain.
The `statementToFileName` function analyzes each SQL statement and extracts relevant information:

- For `CREATE TABLE` statements: Uses the table name (e.g., `create_users_table`)
- For `ALTER TABLE` statements: Uses the table name and operation (e.g., `alter_users_add_email`)
- For `CREATE INDEX` statements: Uses the index name (e.g., `create_users_email_idx`)
- For `CREATE SEQUENCE` statements: Uses the sequence name (e.g., `create_user_id_seq`)
- For `DROP TABLE` statements: Uses the table name (e.g., `drop_users_table`)
- For other statements: Attempts to extract any table name or generates a generic name

This ensures that migration files have descriptive names that reflect their purpose, making it easier to understand the
evolution of your database schema.

## Troubleshooting

### Common Issues

1. **No migrations generated**
    - Ensure your Exposed table classes are in the package specified by `exposedTablesPackage`
    - Verify that your table classes are objects (singletons) that extend `Table`
    - Check that the database connection details are correct
    - If using TestContainers, ensure Docker is running on your machine
    - Make sure the classpath is correctly configured to include your table definitions

2. **Database connection errors**
    - Verify that the database server is running and accessible
    - Check that the database URL, username, and password are correct
    - Ensure that the database user has the necessary permissions
    - If using TestContainers, check that Docker is running and has sufficient resources
    - If you specified a custom JDBC driver, make sure it's on the classpath

3. **TestContainers issues**
    - If you see "Could not find a valid Docker environment", ensure Docker is installed and running
    - For "Connection refused" errors, check that Docker has network connectivity
    - If the container fails to start, try specifying a different image or version
    - Ensure your machine has enough memory and disk space for the container
    - Check that you're using a supported database container image (see the "Using TestContainers" section)

4. **Flyway migration errors**
    - If you see errors like "Migration checksum mismatch", it means the migration script has changed after it was
      applied. Never modify existing migration scripts; instead, create new ones.
    - For "Migration version X already exists" errors, ensure you're not generating duplicate version numbers
    - If Flyway fails to run migrations, check that the migration scripts are valid SQL for your database type
    - When using TestContainers with Flyway, remember that the database is ephemeral and will be recreated each time

5. **Version format issues**
    - If you're mixing version formats (VX__ and VX_Y__) in your migration files, the plugin might not correctly
      determine the next version number
    - Try to stick with one version format throughout your project
    - If you need to switch formats, you might need to manually adjust the version numbers in your migration files

## Contributing

Contributions are welcome! Here's how you can contribute:

1. **Fork the repository**: Create your own fork of the project
2. **Create a branch**: Make your changes in a new branch
3. **Write tests**: Add tests for your changes
4. **Follow coding standards**: Make sure your code follows the project's coding standards
5. **Submit a pull request**: Open a PR with a clear description of your changes

Before submitting a pull request, please ensure:

- All tests pass
- Code is properly formatted
- Documentation is updated to reflect your changes
- Your changes don't break existing functionality

## License

This project is licensed under the Apache 2.0 License - see the LICENSE file for details.
