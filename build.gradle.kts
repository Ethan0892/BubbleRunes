plugins {
    `java`
}

group = "com.bubblecraft"
version = "1.0.5"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()

    // Purpur API snapshots
    maven("https://repo.purpurmc.org/snapshots")

    // Paper API (used by MockBukkit for tests)
    maven("https://repo.papermc.io/repository/maven-public/")

    // Auxilor repo for eco / EcoEnchants
    maven("https://repo.auxilor.io/repository/maven-public/")
    
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.willfp:eco:6.74.2")
    compileOnly("me.clip:placeholderapi:2.11.6")
    
    // Adventure API is included in Purpur, no need to shade

    // Keep JUnit Platform aligned with Gradle 8.10.x
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.98.0")
    // 'compileOnly' does not propagate to tests; MockBukkit 4.98.0 requires Paper API 1.21.10
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Plugin initializes SQLite on enable; include driver for clean test runs
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<Jar>("pluginJar") {
    archiveBaseName.set("BubbleRune")
    from(sourceSets.main.get().output)
}

tasks.test {
    useJUnitPlatform()
}
