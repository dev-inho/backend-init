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
import org.mybatis.spring.mapper.ClassPathMapperScanner
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.AnnotationMetadata

class MyBatisMapperScannerRegistrar : ImportBeanDefinitionRegistrar, ResourceLoaderAware {
    private lateinit var resourceLoader: ResourceLoader
    
    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }

    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        val scanner = ClassPathMapperScanner(registry)
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

    
    @ConditionalOnMissingBean(SampleRepositoryPort::class)
        @Bean
    fun sampleRepositoryPort(
        sampleMapper: SampleMapper,
    ): SampleRepositoryPort {
        return MyBatisSampleRepositoryAdapter(sampleMapper)
    }

    @ConditionalOnMissingBean(UserRepositoryPort::class)
        @Bean
    fun userRepositoryPort(
        userMapper: UserMapper,
    ): UserRepositoryPort {
        return MyBatisUserRepositoryAdapter(userMapper)
    }

    @ConditionalOnMissingBean(FileMetaRepositoryPort::class)
        @Bean
    fun fileMetaRepositoryPort(
        fileMetaMapper: FileMetaMapper,
    ): FileMetaRepositoryPort {
        return MyBatisFileMetaRepositoryAdapter(fileMetaMapper)
    }
}
