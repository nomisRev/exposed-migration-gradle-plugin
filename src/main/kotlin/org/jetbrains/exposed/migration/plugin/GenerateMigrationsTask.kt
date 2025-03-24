package org.jetbrains.exposed.migration.plugin

import org.flywaydb.core.Flyway
import org.flywaydb.core.internal.jdbc.DriverDataSource
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.org.jetbrains.exposed.migration.plugin.statementToFileName
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.OracleContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.io.File.separator
import java.net.URLClassLoader
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Task for generating SQL migration scripts from Exposed table definitions.
 */
abstract class GenerateMigrationsTask : DefaultTask() {

    /**
     * Directory where the generated migration scripts will be stored.
     */
    @get:OutputDirectory
    abstract val migrationsDir: DirectoryProperty

    @get:Input
    abstract val exposedTablesPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFilePrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFileSeparator: Property<String>

    @get:Input
    @get:Optional
    abstract val migrationFileExtension: Property<String>

    @get:Input
    @get:Optional
    abstract val databaseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val databaseUser: Property<String>

    @get:Input
    @get:Optional
    abstract val databasePassword: Property<String>

    @get:Input
    @get:Optional
    abstract val testContainersImageName: Property<String>

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @OptIn(ExperimentalDatabaseMigrationApi::class)
    @TaskAction
    fun generateMigrations() {
        val extension = migrationFileExtension.get()
        val migrationsDirectory = migrationsDir.get().asFile
        if (!migrationsDirectory.exists()) migrationsDirectory.mkdirs()
        val versionGen = findHighestVersion(migrationsDirectory)

        val generated = withClassloader { classloader ->
            withDatabase { database ->
                var ignored = 0
                classloader.getClassesInPackage(exposedTablesPackage.get())
                    .mapNotNull { it.tableOrNull() }
                    .mapIndexedNotNull { index, table ->
                        transaction(database) {
//                            addLogger(GradleLogger())
                            val statements =
                                MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
                            if (statements.isNotEmpty()) {
                                val name = statements.first().statementToFileName()
                                val version = versionGen(index - ignored)
                                val fileName = "$version$name$extension"
                                val migrationFile = File(migrationsDirectory, fileName)
                                migrationFile.writeText(statements.joinToString(";\n"))
                                fileName
                            } else {
                                ignored++
                                null
                            }
                        }
                    }.toList()
            }
        }
        logger.lifecycle("")
        logger.lifecycle("# Exposed Migrations Generated ${generated.size} migrations:")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")
    }

    private val versionPattern by lazy { Pattern.compile("^${migrationFilePrefix.get()}(\\d+)${migrationFileSeparator.get()}.*$") }
    private val versionXYPattern by lazy { Pattern.compile("^${migrationFilePrefix.get()}(\\d+)_(\\d+)${migrationFileSeparator.get()}.*$") }

    fun findHighestVersion(migrationsDirectory: File): (Int) -> String {
        var highestMajor = 0
        var hasXYFormat = true

        migrationsDirectory.listFiles().forEach { file ->
            val fileName = file.name

            // Check for VX_Y__ format first
            val matcherXY = versionXYPattern.matcher(fileName)
            if (matcherXY.matches()) {
                val major = matcherXY.group(1).toInt()

                if (major > highestMajor || (major == highestMajor)) {
                    highestMajor = major
                }
            } else {
                // Check for VX__ format
                val matcher = versionPattern.matcher(fileName)
                if (matcher.matches()) {
                    hasXYFormat = false
                    val version = matcher.group(1).toInt()
                    if (version > highestMajor) {
                        highestMajor = version
                    }
                }
            }
        }
        highestMajor++

        return if (hasXYFormat) {
            { index: Int -> "${migrationFilePrefix.get()}${highestMajor}_${index}${migrationFileSeparator.get()}" }
        } else {
            { index: Int -> "${migrationFilePrefix.get()}${highestMajor}${migrationFileSeparator.get()}" }
        }
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A =
        if (testContainersImageName.isPresent) {
            container(testContainersImageName.get()).use { container ->
                withDatabase(container.jdbcUrl, container.username, container.password) { database ->
                    val migrationsDirectory = migrationsDir.get().asFile
                    Flyway.configure()
                        .dataSource(container.jdbcUrl, container.username, container.password)
                        .locations("filesystem:${migrationsDirectory.absolutePath}")
                        .load()
                        .migrate()
                    block(database)
                }
            }
        } else {
            if (!databaseUrl.isPresent || !databaseUser.isPresent || !databasePassword.isPresent) {
                throw IllegalStateException("Database properties (url, user, password) must be provided when not using TestContainers")
            }
            withDatabase(databaseUrl.get(), databaseUser.get(), databasePassword.get(), block)
        }

    fun container(imageName: String): JdbcDatabaseContainer<*> =
        when {
            imageName.startsWith("postgres:") -> PostgreSQLContainer<Nothing>(imageName)
            imageName.startsWith("mysql:") -> MySQLContainer<Nothing>(imageName)
            imageName.startsWith("mariadb:") -> MariaDBContainer<Nothing>(imageName)
            imageName.startsWith("oracle:") || imageName.startsWith("gvenzl/oracle-xe:") -> OracleContainer(imageName)
            imageName.startsWith("mcr.microsoft.com/mssql/server:") || imageName.startsWith("sqlserver:") ->
                MSSQLServerContainer<Nothing>(imageName)

            else -> throw IllegalArgumentException(
                "Unsupported database container image: $imageName. " +
                        "Supported prefixes are: postgres:, mysql:, mariadb:, sqlserver:, mcr.microsoft.com/mssql/server:, oracle:, gvenzl/oracle-xe:"
            )
        }.apply {
            waitingFor(Wait.forListeningPort())
            start()
        }


    private inline fun <A> withDatabase(url: String, user: String, password: String, block: (Database) -> A): A {
        val db = Database.connect(url = url, user = user, password = password)
        return try {
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
        }
    }

    private fun KClass<*>.tableOrNull(): Table? =
        if (isSubclassOf(Table::class) && !isAbstract) {
            (objectInstance as Table)
        } else null

    private inline fun <A> withClassloader(block: (URLClassLoader) -> A): A {
        val original = Thread.currentThread().contextClassLoader
        return try {
            val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
            val classLoader = URLClassLoader(urls, original)
            Thread.currentThread().contextClassLoader = classLoader
            block(classLoader)
        } finally {
            Thread.currentThread().contextClassLoader = original
        }
    }

    private fun URLClassLoader.getClassesInPackage(packageName: String): Sequence<KClass<*>> =
        getResources(packageName.replace('.', '/')).asSequence().flatMap { resource ->
            File(resource.toURI())
                .walk()
                .filter { file -> file.isFile && file.name.endsWith(".class") }
                .map { file ->
                    val baseDir = File(resource.toURI())
                    val subPackageName = file.relativeTo(baseDir)
                        .path
                        .replace(separator, ".").dropLast(file.name.length + 1)
                    val fullPackage = packageName + "." + if (subPackageName.isBlank()) "" else "$subPackageName."
                    val clazzName = file.name.dropLast(6)
                    Class.forName("$fullPackage$clazzName", true, this).kotlin
                }
        }

    inner class GradleLogger : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            logger.lifecycle(context.expandArgs(TransactionManager.current()))
        }
    }

}
