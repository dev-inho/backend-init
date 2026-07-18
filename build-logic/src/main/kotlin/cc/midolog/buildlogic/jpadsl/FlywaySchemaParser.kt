package cc.midolog.buildlogic.jpadsl

import java.io.File

/**
 * Parses the current schema statically from Flyway migration SQL — no database required,
 * so it is deterministic and CI-friendly.
 *
 * Supports the DDL this project actually uses: `CREATE TABLE [IF NOT EXISTS]` with inline
 * column definitions, inline and table-level `PRIMARY KEY`, table-level
 * `FOREIGN KEY ... REFERENCES`, and later-migration `ALTER TABLE ADD [COLUMN]` /
 * `ADD [CONSTRAINT ...] FOREIGN KEY`. `CREATE INDEX`, `INSERT`, and comments are ignored.
 */
class FlywaySchemaParser {
    private class MutableTable(val name: String) {
        val columns = linkedMapOf<String, ActualColumn>()
        val foreignKeys = mutableListOf<ActualForeignKey>()
    }

    /** Parses [files] (applied in the given order) into a merged [ActualSchema]. */
    fun parse(files: List<File>): ActualSchema {
        val tables = linkedMapOf<String, MutableTable>()
        files.forEach { file ->
            statements(file.readText()).forEach { statement ->
                applyStatement(statement, tables)
            }
        }
        return ActualSchema(
            tables.values.map { ActualTable(it.name, it.columns.values.toList(), it.foreignKeys.toList()) },
        )
    }

    private fun applyStatement(statement: String, tables: MutableMap<String, MutableTable>) {
        val upper = statement.uppercase()
        when {
            upper.startsWith("CREATE TABLE") -> parseCreateTable(statement, tables)
            upper.startsWith("ALTER TABLE") -> parseAlterTable(statement, tables)
        }
    }

    private fun parseCreateTable(statement: String, tables: MutableMap<String, MutableTable>) {
        val open = statement.indexOf('(')
        val close = statement.lastIndexOf(')')
        if (open < 0 || close < open) return
        val header = statement.substring(0, open)
        val name = header
            .replace(Regex("(?i)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?"), "")
            .trim()
            .trim('"')
        val table = tables.getOrPut(name.lowercase()) { MutableTable(name) }

        splitTopLevel(statement.substring(open + 1, close)).forEach { item ->
            val trimmed = item.trim()
            val upper = trimmed.uppercase()
            when {
                upper.startsWith("PRIMARY KEY") -> {
                    columnsInParens(trimmed).forEach { pk ->
                        table.columns[pk.lowercase()]?.let { table.columns[pk.lowercase()] = it.copy(nullable = false, primaryKey = true) }
                    }
                }
                upper.startsWith("CONSTRAINT") || upper.startsWith("FOREIGN KEY") ->
                    parseForeignKey(trimmed)?.let { table.foreignKeys += it }
                upper.startsWith("UNIQUE") || upper.startsWith("CHECK") -> Unit
                else -> parseColumn(trimmed)?.let { table.columns[it.name.lowercase()] = it }
            }
        }
    }

    private fun parseAlterTable(statement: String, tables: MutableMap<String, MutableTable>) {
        val match = Regex("(?i)ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?\"?([A-Za-z0-9_]+)\"?\\s+(.*)", RegexOption.DOT_MATCHES_ALL)
            .find(statement) ?: return
        val name = match.groupValues[1]
        val body = match.groupValues[2].trim()
        val table = tables.getOrPut(name.lowercase()) { MutableTable(name) }
        val upper = body.uppercase()
        when {
            upper.contains("FOREIGN KEY") ->
                parseForeignKey(body)?.let { table.foreignKeys += it }
            upper.startsWith("ADD COLUMN") || upper.startsWith("ADD ") ->
                parseColumn(body.replaceFirst(Regex("(?i)ADD\\s+(COLUMN\\s+)?"), ""))
                    ?.let { table.columns[it.name.lowercase()] = it }
        }
    }

    private fun parseColumn(definition: String): ActualColumn? {
        val match = Regex("^\"?([A-Za-z_][A-Za-z0-9_]*)\"?\\s+(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(definition.trim()) ?: return null
        val name = match.groupValues[1]
        if (name.uppercase() in RESERVED_LEADERS) return null
        val rest = match.groupValues[2].trim()
        val typeMatch = Regex("^([A-Za-z][A-Za-z0-9_]*(\\s+PRECISION)?(\\s*\\([^)]*\\))?)")
            .find(rest) ?: return null
        val sqlType = typeMatch.groupValues[1].trim()
        val modifiers = rest.substring(typeMatch.value.length).uppercase()
        val primaryKey = modifiers.contains("PRIMARY KEY")
        val nullable = !primaryKey && !modifiers.contains("NOT NULL")
        return ActualColumn(name, sqlType, nullable, primaryKey)
    }

    private fun parseForeignKey(definition: String): ActualForeignKey? {
        val match = Regex(
            "(?i)FOREIGN\\s+KEY\\s*\\(\\s*\"?([A-Za-z0-9_]+)\"?\\s*\\)\\s*REFERENCES\\s+\"?([A-Za-z0-9_]+)\"?\\s*\\(\\s*\"?([A-Za-z0-9_]+)\"?\\s*\\)",
        ).find(definition) ?: return null
        return ActualForeignKey(match.groupValues[1], match.groupValues[2], match.groupValues[3])
    }

    private fun columnsInParens(text: String): List<String> {
        val open = text.indexOf('(')
        val close = text.indexOf(')', open)
        if (open < 0 || close < open) return emptyList()
        return text.substring(open + 1, close).split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
    }

    /** Splits a SQL text into statements on top-level `;`, stripping `--` line comments. */
    private fun statements(sql: String): List<String> =
        sql.lineSequence()
            .map { it.substringBefore("--").trimEnd() }
            .joinToString("\n")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Splits a parenthesised body on commas that are not nested inside parentheses. */
    private fun splitTopLevel(body: String): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in body) {
            when (ch) {
                '(' -> { depth++; current.append(ch) }
                ')' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) { items += current.toString(); current.clear() } else current.append(ch)
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) items += current.toString()
        return items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private companion object {
        val RESERVED_LEADERS = setOf("PRIMARY", "FOREIGN", "CONSTRAINT", "UNIQUE", "CHECK")
    }
}
