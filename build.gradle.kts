plugins {
    `java`
}

group = "com.bubblecraft"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()

    // Purpur API snapshots
    maven("https://repo.purpurmc.org/snapshots")

    // Auxilor repo for eco / EcoEnchants
    maven("https://repo.auxilor.io/repository/maven-public/")
    
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.willfp:eco:6.74.2")
    compileOnly("me.clip:placeholderapi:2.11.6")
    
    // Adventure API is included in Purpur, no need to shade
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<Jar>("pluginJar") {
    archiveBaseName.set("BubbleRune")
    from(sourceSets.main.get().output)
}
