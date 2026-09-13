package cc.midolog.storage.mybatis.autoconfigure

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.storage.mybatis.file.FileMetaMapper
import cc.midolog.storage.mybatis.file.MyBatisFileMetaRepositoryAdapter
import cc.midolog.storage.mybatis.sample.MyBatisSampleRepositoryAdapter
import cc.midolog.storage.mybatis.sample.SampleMapper
import cc.midolog.storage.mybatis.user.MyBatisUserRepositoryAdapter
import cc.midolog.storage.mybatis.user.UserMapper
import org.apache.ibatis.mapping.DatabaseIdProvider
import org.apache.ibatis.mapping.VendorDatabaseIdProvider
import org.mybatis.spring.mapper.ClassPathMapperScanner
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.EnvironmentAware
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.AnnotationMetadata
import java.util.Properties

class MyBatisMapperScannerRegistrar : ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var environment: Environment

    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        val scanner = ClassPathMapperScanner(registry, environment)
        if (::resourceLoader.isInitialized) {
            scanner.setResourceLoader(resourceLoader)
        }
        scanner.registerFilters()
        scanner.doScan("cc.midolog.storage.mybatis")
    }
}

@AutoConfiguration
@ConditionalOnProperty(prefix = "storage.persistence", name = ["provider"], havingValue = "mybatis")
@Import(MyBatisMapperScannerRegistrar::class)
class MyBatisStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DatabaseIdProvider::class)
    fun databaseIdProvider(): DatabaseIdProvider {
        val provider = VendorDatabaseIdProvider()
        val properties = Properties()
        properties.setProperty("PostgreSQL", "postgresql")
        properties.setProperty("H2", "h2")
        provider.setProperties(properties)
        return DatabaseIdProvider { dataSource ->
            val databaseId = try {
                provider.getDatabaseId(dataSource)
            } catch (e: Exception) {
                throw IllegalStateException("데이터베이스 식별 실패: ${e.message}", e)
            }
            if (databaseId == null) {
                val productName = try {
                    dataSource.connection.use { it.metaData.databaseProductName }
                } catch (e: Exception) {
                    "알 수 없음"
                }
                throw IllegalStateException("지원 벤더: postgresql, h2 — 감지: $productName")
            }
            databaseId
        }
    }

    @Bean
    @ConditionalOnMissingBean(SampleRepositoryPort::class)
    fun sampleRepositoryPort(
        sampleMapper: SampleMapper,
    ): SampleRepositoryPort {
        return MyBatisSampleRepositoryAdapter(sampleMapper)
    }

    @Bean
    @ConditionalOnMissingBean(UserRepositoryPort::class)
    fun userRepositoryPort(
        userMapper: UserMapper,
    ): UserRepositoryPort {
        return MyBatisUserRepositoryAdapter(userMapper)
    }

    @Bean
    @ConditionalOnMissingBean(FileMetaRepositoryPort::class)
    fun fileMetaRepositoryPort(
        fileMetaMapper: FileMetaMapper,
    ): FileMetaRepositoryPort {
        return MyBatisFileMetaRepositoryAdapter(fileMetaMapper)
    }
}
