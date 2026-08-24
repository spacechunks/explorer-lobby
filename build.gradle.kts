import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields

buildscript {
    repositories {
        mavenCentral()
    }
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.grpc") {
                useVersion("1.62.2")
            }
        }
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
        }
    }
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        classpath("aws.sdk.kotlin:s3:1.6.26")
    }
}

plugins {
    kotlin("jvm") version "2.3.20"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.4.1"
    id("com.google.protobuf") version "0.9.5"
}

fun git(vararg args: String): String =
    runCatching {
        providers.exec { commandLine("git", *args) }
            .standardOutput.asText.get().trim()
    }.getOrDefault("")

group = "space.chunks"

version = run {
    val today = LocalDate.now(ZoneOffset.UTC)
    // ISO week-based year, so the days around new year land in the right year
    val prefix = "${today.get(WeekFields.ISO.weekBasedYear())}." +
            "${today.get(WeekFields.ISO.weekOfWeekBasedYear())}."

    val calver = Regex("""^\d{4}\.\d+\.\d+$""")
    val tagOnHead = git("tag", "--points-at", "HEAD")
        .lineSequence()
        .map(String::trim)
        .firstOrNull { calver.matches(it) }

    tagOnHead ?: run {
        // otherwise: one past the highest release of this week
        val highest = git("tag", "-l", "$prefix*")
            .lineSequence()
            .mapNotNull { it.trim().removePrefix(prefix).toIntOrNull() }
            .maxOrNull() ?: 0
        "$prefix${highest + 1}"
    }
}

tasks.register("printVersion") {
    val resolved = version.toString()
    doLast { println(resolved) }
}

val pluginName = project.property("plugin.name").toString()

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://maven.noxcrew.com/public")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    implementation("com.nimbusds:oauth2-oidc-sdk:11.38.2")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-protobuf:1.76.0")
    implementation("io.grpc:grpc-stub:1.76.0")
    implementation("io.grpc:grpc-netty:1.76.0")
    implementation("aws.sdk.kotlin:s3:1.6.26")
    api("com.google.protobuf:protobuf-kotlin:3.25.8")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.noxcrew.interfaces:interfaces:2.1.0-SNAPSHOT")
//    compileOnly("space.chunks:world-service-plugin:2026.20.5")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.2.0")
    implementation("org.ktorm:ktorm-core:4.1.1")
    implementation("org.ktorm:ktorm-support-postgresql:4.1.1")
    implementation("org.postgresql:postgresql:42.7.4")
}

tasks.test {
    useJUnitPlatform()
}


java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.named("shadowJar", ShadowJar::class) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
//    exclude("kotlin/**")
//    exclude("kotlinx/coroutines/**")
//    exclude("com/noxcrew/interfaces/**")
    // ai told me to do this, because of class loader problems with paper
    relocate(
        "com.google.protobuf",
        "space.chunks.shadow.protobuf"
    )
    archiveFileName.set("${project.name}.jar")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand(
            "version" to project.version,
            "name" to pluginName,
        )
    }
}

tasks.register("uploadToS3") {
    dependsOn("shadowJar")
    group = "release"
    description = "uploads the fat jar to s3"

    doLast {
        val jarFile = tasks.named("shadowJar", ShadowJar::class).get().archiveFile.get().asFile

        runBlocking {
            val s3 = S3Client.fromEnvironment()
            val bucketName = System.getenv("BUCKET_NAME") ?: error("BUCKET_NAME env var not set")
            val key = "plugins/paper/$pluginName-${project.version}.jar"

            s3.putObject {
                bucket = bucketName
                this.key = key
                body = ByteStream.fromFile(jarFile)
                contentLength = jarFile.length()
            }

            println("uploaded ${jarFile.name} to s3://$bucketName/$key")
        }
    }
}

tasks {
    runServer {
        jvmArgs("-Dlobby.local=true")

        downloadPlugins {
            modrinth("ViaVersion", "5.10.0")
            modrinth("BetterModel", "8xoSUfzr") // version: 3.2.0
        }
        minecraftVersion("26.2")
    }
}

sourceSets {
    main {
        kotlin {
            srcDirs(
                "build/generated/source/proto/main/kotlin",
            )
        }
        java {
            srcDirs(
                "build/generated/source/proto/main/java",
            )
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.8"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.76.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.5.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
