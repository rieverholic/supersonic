plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.riever"
version = "0.3.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module="opus-java")
    }
}

tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    dependencies {
        include(dependency("net.dv8tion:JDA"))
        include(dependency("com.neovisionaries:nv-websocket-client"))
        include(dependency("com.squareup.okhttp3:okhttp"))
        include(dependency("org.apache.commons:commons-collections4"))
        include(dependency("net.sf.trove4j:core"))
        include(dependency("com.fasterxml.jackson.core:jackson-core"))
        include(dependency("com.fasterxml.jackson.core:jackson-databind"))
        include(dependency("com.fasterxml.jackson.core:jackson-annotations"))
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("com.squareup.okio:okio"))
        include(dependency("com.squareup.okio:okio-jvm"))
    }
    minimize()
    relocate("net.dv8tion.jda", "dev.riever.libs.jda")
    relocate("com.neovisionaries", "dev.riever.libs.neovisionaries")
    relocate("okhttp3", "dev.riever.libs.okhttp3")
    relocate("org.apache.commons.collections4", "dev.riever.libs.collections4")
    relocate("com.fasterxml.jackson", "dev.riever.libs.jackson")
    relocate("kotlin", "dev.riever.libs.kotlin")
    relocate("okio", "dev.riever.libs.okio")
    relocate("gnu.trove", "dev.riever.libs.trove")
}

configurations {

}

tasks.test {
    useJUnitPlatform()
}