plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.topdanya33200_netizen"
version = "1.0.0"
description = "Player reports with a staff GUI and Discord webhook sync for Paper and Folia"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Paper ships SQLite at runtime; tests need it explicitly
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.49.1.0")
}

// Unit tests use Gson and other libraries that Paper provides at runtime
configurations.testImplementation.configure {
    extendsFrom(configurations.compileOnly.get())
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    compileTestJava {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        // ./gradlew runServer starts a local Paper server with the plugin installed
        minecraftVersion("26.3")
    }
}
