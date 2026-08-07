plugins {
    java
}

group = project.property("group") as String
version = project.property("version") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT")
    // 7.4.x requires JVM 25+; 7.3.19 supports 1.21.x on Java 21
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.19")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
    // Bundle shareware IWAD + native lib
    from("assets") {
        into("wads")
    }
    from("native") {
        into("native")
    }
}

tasks.register<Exec>("compileNative") {
    group = "build"
    description = "Compile PureDOOM JNI shared library (linux-x86_64)"
    workingDir = file("src/main/c")
    val javaHome = System.getProperty("java.home")
    inputs.files("src/main/c/doom_jni.c", "src/main/c/PureDOOM.h")
    outputs.file(layout.projectDirectory.file("native/libpuredoom.so"))
    commandLine(
        "gcc", "-shared", "-fPIC", "-O2",
        "-o", "${rootProject.projectDir}/native/libpuredoom.so",
        "doom_jni.c",
        "-I$javaHome/include",
        "-I$javaHome/include/linux",
        "-lm"
    )
}

tasks.named("processResources") {
    dependsOn("compileNative")
}

tasks.jar {
    archiveBaseName.set("MineDoom")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}
