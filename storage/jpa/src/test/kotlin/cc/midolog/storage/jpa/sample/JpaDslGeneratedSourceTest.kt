package cc.midolog.storage.jpa.sample

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class JpaDslGeneratedSourceTest {

    @Test
    fun `generated JPA sources stay in build directory with scalar and relation policy`() {
        val generatedRoot = Path.of(
            requireNotNull(System.getProperty("jpaDsl.generatedSourceDir")) {
                "jpaDsl.generatedSourceDir system property must point to Gradle generated source directory"
            },
        )
        val expectedFiles = listOf(
            "cc/midolog/storage/jpa/sample/SampleJpaEntity.kt",
            "cc/midolog/storage/jpa/sample/SampleJpaRepository.kt",
            "cc/midolog/storage/jpa/sample/SampleJpaMapper.kt",
            "cc/midolog/storage/jpa/user/UserJpaEntity.kt",
            "cc/midolog/storage/jpa/user/UserJpaRepository.kt",
            "cc/midolog/storage/jpa/user/UserJpaMapper.kt",
            "cc/midolog/storage/jpa/file/FileMetaJpaEntity.kt",
            "cc/midolog/storage/jpa/file/FileMetaJpaRepository.kt",
            "cc/midolog/storage/jpa/file/FileMetaJpaMapper.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/ScalarSampleJpaEntity.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/ScalarSampleJpaRepository.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/ScalarSampleJpaMapper.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationParentJpaEntity.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationParentJpaRepository.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationParentJpaMapper.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationChildJpaEntity.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationChildJpaRepository.kt",
            "cc/midolog/storage/jpa/jpadsl/fixture/RelationChildJpaMapper.kt",
        )

        val missingFiles = expectedFiles
            .map { generatedRoot.resolve(it) }
            .filterNot(Files::isRegularFile)

        assertTrue(missingFiles.isEmpty(), "Missing generated files:\n${missingFiles.joinToString("\n")}")

        val fileMetaEntity = generatedRoot
            .resolve("cc/midolog/storage/jpa/file/FileMetaJpaEntity.kt")
            .readText()
        assertTrue(fileMetaEntity.contains("@Column(name = \"storage_key\", nullable = false)"))
        assertTrue(fileMetaEntity.contains("@Column(name = \"size_bytes\", nullable = true)"))
        assertTrue(fileMetaEntity.contains("@Column(name = \"content_type\", nullable = true)"))
        assertTrue(fileMetaEntity.contains("@Column(name = \"created_at\", nullable = false)"))
        assertTrue(fileMetaEntity.contains("@Enumerated(EnumType.STRING)"))

        val scalarEntity = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/ScalarSampleJpaEntity.kt")
            .readText()
        assertTrue(scalarEntity.contains("@Column(name = \"display_name\", nullable = false)"))
        assertTrue(scalarEntity.contains("@Column(name = \"nickname\", nullable = true)"))
        assertTrue(scalarEntity.contains("@Enumerated(EnumType.STRING)"))
        assertTrue(scalarEntity.contains("@Column(name = \"code_value\", nullable = false)"))

        val scalarMapper = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/ScalarSampleJpaMapper.kt")
            .readText()
        assertTrue(scalarMapper.contains("ScalarSampleCodeJpaConverter.toStorage(domain.code)"))
        assertTrue(scalarMapper.contains("ScalarSampleCodeJpaConverter.toDomain(entity.code)"))

        val parentEntity = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/RelationParentJpaEntity.kt")
            .readText()
        assertTrue(parentEntity.contains("@OneToMany(mappedBy = \"parent\", fetch = FetchType.LAZY)"))

        val childEntity = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/RelationChildJpaEntity.kt")
            .readText()
        assertTrue(childEntity.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(childEntity.contains("@JoinColumn(name = \"parent_id\", referencedColumnName = \"id\", nullable = false)"))

        val parentMapper = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/RelationParentJpaMapper.kt")
            .readText()
        assertTrue(parentMapper.contains("children = emptyList()"))

        val childMapper = generatedRoot
            .resolve("cc/midolog/storage/jpa/jpadsl/fixture/RelationChildJpaMapper.kt")
            .readText()
        assertTrue(childMapper.contains("parentId = entity.parent.id"))
        assertTrue(childMapper.contains("parent = RelationParentJpaEntity(id = domain.parentId)"))
    }

    @Test
    fun `generated JPA artifacts are not written under source tree`() {
        val sourceRoot = Path.of("src/main/kotlin")
        val generatedNames = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .map { it.name }
                .filter {
                    it.endsWith("JpaEntity.kt") ||
                        it.endsWith("JpaMapper.kt") ||
                        it.endsWith("JpaRepository.kt")
                }
                .toList()
        }

        assertTrue(generatedNames.isEmpty(), "Generated JPA files must stay out of src/main/kotlin: $generatedNames")
    }
}
