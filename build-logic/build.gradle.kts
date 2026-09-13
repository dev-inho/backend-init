plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.21"
}

group = "cc.midolog.buildlogic"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

gradlePlugin {
    plugins {
        create("jpaDsl") {
            id = "cc.midolog.jpa-dsl"
            implementationClass = "cc.midolog.buildlogic.jpadsl.JpaDslPlugin"
        }
        create("publishing") {
            id = "cc.midolog.publishing"
            implementationClass = "cc.midolog.buildlogic.publishing.PublishingConventionPlugin"
        }
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
