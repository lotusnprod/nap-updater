plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "net.nprod.nap"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("org.apache.jena:jena-fuseki:5.0.0")
    implementation("org.apache.jena:jena-fuseki-main:5.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")

}

application {
    mainClass.set("net.nprod.nap.updater.D20240609_species")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}