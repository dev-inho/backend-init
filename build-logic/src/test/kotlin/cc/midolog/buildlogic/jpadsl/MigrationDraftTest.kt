package cc.midolog.buildlogic.jpadsl

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationDraftTest {

    /** The current hand-written baseline migration, verbatim, used as the "actual" schema. */
    private val v1Sql = """
        CREATE TABLE IF NOT EXISTS sample (
            id VARCHAR(64) PRIMARY KEY,
            name VARCHAR(255) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS app_user (
            id VARCHAR(64) PRIMARY KEY,
            email VARCHAR(255) NOT NULL,
            display_name VARCHAR(255) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS scalar_sample (
            id VARCHAR(64) PRIMARY KEY,
            display_name VARCHAR(255) NOT NULL,
            nickname VARCHAR(255),
            status VARCHAR(255) NOT NULL,
            code_value VARCHAR(255) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS relation_parent (
            id VARCHAR(64) PRIMARY KEY,
            name VARCHAR(255) NOT NULL
        );

        CREATE TABLE IF NOT EXISTS relation_child (
            id VARCHAR(64) PRIMARY KEY,
            parent_id VARCHAR(64) NOT NULL,
            name VARCHAR(255) NOT NULL,
            CONSTRAINT fk_relation_child_parent
                FOREIGN KEY (parent_id)
                REFERENCES relation_parent (id)
        );

        CREATE INDEX IF NOT EXISTS idx_relation_child_parent_id
            ON relation_child (parent_id);

        INSERT INTO sample (id, name)
        VALUES ('sample_0001', 'hello')
        ON CONFLICT (id) DO NOTHING;
    """.trimIndent()

    @Test
    fun `flyway parser reads columns, primary keys, and foreign keys`() {
        val schema = FlywaySchemaParser().parse(listOf(writeMigration("V1__baseline.sql", v1Sql)))

        val sample = schema.table("sample")!!
        assertEquals(setOf("id", "name"), sample.columns.map { it.name }.toSet())
        val id = sample.columns.first { it.name == "id" }
        assertEquals("VARCHAR(64)", id.sqlType)
        assertTrue(id.primaryKey)
        assertFalse(id.nullable)
        assertTrue(sample.columns.first { it.name == "name" }.nullable.not())

        val nickname = schema.table("scalar_sample")!!.columns.first { it.name == "nickname" }
        assertTrue(nickname.nullable)

        val child = schema.table("relation_child")!!
        assertEquals(1, child.foreignKeys.size)
        val fk = child.foreignKeys.single()
        assertEquals(ActualForeignKey("parent_id", "relation_parent", "id"), fk)
    }

    @Test
    fun `expected schema from the current DSL matches the V1 baseline with zero drift`() {
        val domainRoot = writeSampleDomain()
        val expected = ExpectedSchemaBuilder().build(sampleSpecs(), domainRoot)
        val actual = FlywaySchemaParser().parse(listOf(writeMigration("V1__baseline.sql", v1Sql)))

        val diff = SchemaDiffer.diff(expected, actual)

        assertTrue(diff.isEmpty, "expected no drift but got:\n${diff.summary()}")
    }

    @Test
    fun `diff detects a missing table, column, and foreign key`() {
        val expected = ExpectedSchema(
            listOf(
                ExpectedTable(
                    name = "app_user",
                    columns = listOf(
                        ExpectedColumn("id", "VARCHAR(64)", nullable = false, primaryKey = true),
                        ExpectedColumn("email", "VARCHAR(255)", nullable = false, primaryKey = false),
                        ExpectedColumn("nickname", "VARCHAR(255)", nullable = true, primaryKey = false),
                    ),
                    foreignKeys = emptyList(),
                ),
                ExpectedTable(
                    name = "audit_log",
                    columns = listOf(ExpectedColumn("id", "VARCHAR(64)", nullable = false, primaryKey = true)),
                    foreignKeys = listOf(ExpectedForeignKey("user_id", "app_user", "id")),
                ),
            ),
        )
        val actual = ActualSchema(
            listOf(
                ActualTable(
                    name = "app_user",
                    columns = listOf(
                        ActualColumn("id", "VARCHAR(64)", nullable = false, primaryKey = true),
                        ActualColumn("email", "VARCHAR(255)", nullable = false, primaryKey = false),
                    ),
                    foreignKeys = emptyList(),
                ),
            ),
        )

        val diff = SchemaDiffer.diff(expected, actual)

        assertEquals(listOf("audit_log"), diff.missingTables.map { it.name })
        assertEquals(listOf("nickname"), diff.missingColumns.map { it.second.name })
        assertTrue(diff.missingForeignKeys.isEmpty(), "audit_log is a whole missing table, its FK ships in the CREATE")
    }

    @Test
    fun `diff reports type mismatch without auto-generating destructive DDL`() {
        val expected = ExpectedSchema(
            listOf(
                ExpectedTable(
                    "app_user",
                    listOf(ExpectedColumn("email", "VARCHAR(64)", nullable = false, primaryKey = false)),
                    emptyList(),
                ),
            ),
        )
        val actual = ActualSchema(
            listOf(
                ActualTable(
                    "app_user",
                    listOf(ActualColumn("email", "VARCHAR(255)", nullable = false, primaryKey = false)),
                    emptyList(),
                ),
            ),
        )

        val diff = SchemaDiffer.diff(expected, actual)

        assertEquals(1, diff.mismatches.size)
        val sql = MigrationDraftRenderer().render(diff)
        assertTrue(sql.contains("WARNING"), "mismatch must be a review warning, not DDL")
        assertFalse(sql.contains("ALTER TABLE app_user ALTER"), "must not emit destructive ALTER TYPE")
    }

    @Test
    fun `renderer emits additive create and alter statements`() {
        val diff = SchemaDiff(
            missingTables = listOf(
                ExpectedTable(
                    "audit_log",
                    listOf(
                        ExpectedColumn("id", "VARCHAR(64)", nullable = false, primaryKey = true),
                        ExpectedColumn("user_id", "VARCHAR(64)", nullable = false, primaryKey = false),
                    ),
                    listOf(ExpectedForeignKey("user_id", "app_user", "id")),
                ),
            ),
            missingColumns = listOf(
                ExpectedTable("app_user", emptyList(), emptyList()) to
                    ExpectedColumn("nickname", "VARCHAR(255)", nullable = true, primaryKey = false),
            ),
            missingForeignKeys = emptyList(),
            mismatches = emptyList(),
        )

        val sql = MigrationDraftRenderer().render(diff)

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS audit_log ("))
        assertTrue(sql.contains("id VARCHAR(64) PRIMARY KEY"))
        assertTrue(sql.contains("CONSTRAINT fk_audit_log_user_id FOREIGN KEY (user_id) REFERENCES app_user (id)"))
        assertTrue(sql.contains("ALTER TABLE app_user ADD COLUMN nickname VARCHAR(255);"))
    }

    @Test
    fun `empty diff renders a no-drift comment only`() {
        val sql = MigrationDraftRenderer().render(
            SchemaDiff(emptyList(), emptyList(), emptyList(), emptyList()),
        )
        assertTrue(sql.contains("No schema drift detected."))
        assertFalse(sql.contains("CREATE TABLE"))
        assertFalse(sql.contains("ALTER TABLE"))
    }

    @Test
    fun `sql type mapper follows the fixed identifier and enum rules`() {
        assertEquals("VARCHAR(64)", SqlTypeMapper.sqlType("String", ColumnRole.PRIMARY_KEY, null))
        assertEquals("VARCHAR(64)", SqlTypeMapper.sqlType("String", ColumnRole.FOREIGN_KEY, null))
        assertEquals("VARCHAR(255)", SqlTypeMapper.sqlType("String", ColumnRole.REGULAR, null))
        assertEquals("VARCHAR(255)", SqlTypeMapper.sqlType("SampleStatus", ColumnRole.REGULAR, "STRING"))
        assertEquals("BIGINT", SqlTypeMapper.sqlType("Long", ColumnRole.REGULAR, null))
        assertEquals("TIMESTAMP", SqlTypeMapper.sqlType("Instant?", ColumnRole.REGULAR, null))
    }

    // --- fixtures -----------------------------------------------------------

    private val tempRoot = Files.createTempDirectory("migration-draft-test").toFile()

    private fun writeMigration(name: String, sql: String): File =
        File(tempRoot, name).apply { parentFile.mkdirs(); writeText(sql) }

    private fun sampleSpecs(): List<JpaEntitySpec> = listOf(
        JpaEntitySpec("cc.midolog.sample.model.Sample").apply { table = "sample"; id = "id" },
        JpaEntitySpec("cc.midolog.user.model.User").apply {
            table = "app_user"; id = "id"
            fields["displayName"] = JpaFieldSpec().apply { column = "display_name" }
        },
        JpaEntitySpec("cc.midolog.sample.model.ScalarSample").apply {
            table = "scalar_sample"; id = "id"
            fields["displayName"] = JpaFieldSpec().apply { column = "display_name" }
            fields["nickname"] = JpaFieldSpec().apply { nullable = true }
            fields["status"] = JpaFieldSpec().apply { enumStrategy = "STRING" }
            fields["code"] = JpaFieldSpec().apply {
                column = "code_value"; storageType = "String"
                converter = "cc.midolog.storage.jpa.sample.ScalarSampleCodeJpaConverter"
            }
        },
        JpaEntitySpec("cc.midolog.sample.model.RelationParent").apply {
            table = "relation_parent"; id = "id"
            relations["children"] = JpaRelationSpec().apply {
                type = "oneToMany"; target = "cc.midolog.sample.model.RelationChild"
                mappedBy = "parent"; toDomain = "emptyList()"
            }
        },
        JpaEntitySpec("cc.midolog.sample.model.RelationChild").apply {
            table = "relation_child"; id = "id"
            fields["parentId"] = JpaFieldSpec().apply { relation = "parent" }
            relations["parent"] = JpaRelationSpec().apply {
                type = "manyToOne"; target = "cc.midolog.sample.model.RelationParent"
                sourceField = "parentId"; joinColumn = "parent_id"; referencedColumn = "id"
            }
        },
    )

    private fun writeSampleDomain(): File {
        val root = File(tempRoot, "domain").apply { mkdirs() }
        writeDomain(root, "cc.midolog.sample.model.Sample", "val id: String,\n    val name: String,")
        writeDomain(root, "cc.midolog.user.model.User", "val id: String,\n    val email: String,\n    val displayName: String,")
        writeDomain(
            root,
            "cc.midolog.sample.model.ScalarSample",
            "val id: String,\n    val displayName: String,\n    val nickname: String?,\n    val status: SampleStatus,\n    val code: SampleCode,",
        )
        writeDomain(
            root,
            "cc.midolog.sample.model.RelationParent",
            "val id: String,\n    val name: String,\n    val children: List<RelationChild>,",
        )
        writeDomain(
            root,
            "cc.midolog.sample.model.RelationChild",
            "val id: String,\n    val parentId: String,\n    val name: String,",
        )
        return root
    }

    private fun writeDomain(root: File, fqcn: String, constructorBody: String) {
        val pkg = fqcn.substringBeforeLast('.')
        val simple = fqcn.substringAfterLast('.')
        val file = File(root, fqcn.replace('.', '/') + ".kt")
        file.parentFile.mkdirs()
        file.writeText("package $pkg\n\ndata class $simple(\n    $constructorBody\n)\n")
    }
}
