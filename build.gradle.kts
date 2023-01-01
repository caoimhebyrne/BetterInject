@file:Suppress("GradlePackageUpdate")

plugins {
    id("java")
    id("maven-publish")
}

group = "dev.cbyrne"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven")
}

dependencies {
    compileOnly("org.spongepowered:mixin:0.8.5")

    // Mixin currently uses 9.2
    compileOnly("org.ow2.asm:asm-tree:9.2")
    compileOnly("org.ow2.asm:asm-commons:9.2")
    compileOnly("org.ow2.asm:asm-util:9.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = sourceCompatibility

    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.cbyrne"
            artifactId = "BetterInject"

            from(components["java"])
        }
    }
}
