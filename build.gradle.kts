plugins {
    id("dev.kikugie.loom-back-compat")
    id("maven-publish")
}

val modId = "smartdoubledoors"
val mcVersion = sc.current.version

group = "com.antarip.smartdoubledoors"
version = "1.0.0+mc$mcVersion"

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

val fabricApiVersions = mapOf(
    "26.3" to "0.161.0+26.3",
    "26.2" to "0.156.0+26.2",
    "26.1.2" to "0.155.3+26.1.2",
    "1.21.11" to "0.141.5+1.21.11",
    "1.20.6" to "0.100.8+1.20.6",
    "1.20.1" to "0.92.2+1.20.1",
)

val javaVersion = when {
    sc.current.parsed >= "26.1" -> 25
    sc.current.parsed >= "1.20.5" -> 21
    else -> 17
}

val mcRange = when (mcVersion) {
    "1.20.1" -> ">=1.20 <=1.20.4"
    "1.20.6" -> ">=1.20.5 <=1.20.6"
    "1.21.11" -> ">=1.21 <=1.21.11"
    "26.1.2" -> ">=26.1 <26.2"
    "26.2" -> "26.2"
    "26.3" -> "26.3"
    else -> mcVersion
}

val outputBaseName = when (mcVersion) {
    "1.20.1" -> "$modId-fabric-1.20.0-to-1.20.4"
    "1.20.6" -> "$modId-fabric-1.20.5-to-1.20.6"
    "1.21.11" -> "$modId-fabric-1.21.1-to-1.21.11"
    "26.1.2" -> "$modId-fabric-26.1-to-26.1.x"
    "26.2" -> "$modId-26.2-1.0.0+mc26.2"
    "26.3" -> "$modId-26.3-1.0.0+mc26.3"
    else -> "$modId-$mcVersion"
}

base {
    archivesName.set(outputBaseName)
}

loom {
    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersions[mcVersion] ?: "0.161.0+26.3"}")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", mcRange)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to mcRange
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

tasks.register<Copy>("copyBuiltJarToOutputs") {
    dependsOn(loomx.modJar)
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { filename ->
        "$outputBaseName.jar"
    }
}
