package org.jetbrains.exposed.migration.plugin

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
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.org.jetbrains.exposed.migration.plugin.statementToFileName
import java.io.File
import java.io.File.separator
import java.net.URLClassLoader
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
    abstract val databaseUrl: Property<String>

    @get:Input
    abstract val databaseUser: Property<String>

    @get:Input
    abstract val databasePassword: Property<String>

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @OptIn(ExperimentalDatabaseMigrationApi::class)
    @TaskAction
    fun generateMigrations() {
        val prefix = migrationFilePrefix.get()
        val separator = migrationFileSeparator.get()
        val extension = migrationFileExtension.get()
        val migrationsDirectory = migrationsDir.get().asFile
        if (!migrationsDirectory.exists()) migrationsDirectory.mkdirs()

        val generated = withClassloader { classloader ->
            withDatabase { database ->
                classloader.getClassesInPackage(exposedTablesPackage.get())
                    .mapNotNull { it.tableOrNull() }
                    .mapIndexedNotNull { index, table ->
                        transaction(database) {
//                            addLogger(GradleLogger())
                            val statements =
                                MigrationUtils.statementsRequiredForDatabaseMigration(table, withLogs = false)
                            if (statements.isNotEmpty()) {
                                val name = statements.first().statementToFileName()
                                val fileName = "$prefix${index + 1}${separator}$name$extension"
                                val migrationFile = File(migrationsDirectory, fileName)
                                migrationFile.writeText(statements.joinToString(";\n"))
                                fileName
                            } else null
                        }
                    }.toList()
            }
        }
        logger.lifecycle("")
        logger.lifecycle("# Exposed Migrations Generated ${generated.size} migrations:")
        generated.forEach { logger.lifecycle("  * $it") }
        logger.lifecycle("")
    }

    private inline fun <A> withDatabase(block: (Database) -> A): A {
        val db = Database.connect(
            url = databaseUrl.get(),
            user = databaseUser.get(),
            password = databasePassword.get()
        )
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
