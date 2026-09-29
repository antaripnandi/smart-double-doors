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
)

val javaVersion = 25

base {
    archivesName.set("$modId-$mcVersion")
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
    inputs.property("minecraft_version", mcVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to mcVersion
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
}
