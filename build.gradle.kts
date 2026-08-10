plugins {
    java
}

group = "com.valerinsmp"
version = "1.1.0"

providers.environmentVariable("VALERIN_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.11.2")
    compileOnly("com.nexomc:nexo:1.8.0")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-Xlint:deprecation")
    // Avoid Windows/OneDrive ZipFS locks when Gradle closes dependency JARs.
    options.isFork = true
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
