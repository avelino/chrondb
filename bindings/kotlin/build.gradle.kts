plugins {
    kotlin("jvm") version "1.9.22"
}

group = "run.avelino.chrondb"
version = file("../../VERSION").readText().trim()

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    val libDir = System.getenv("CHRONDB_LIB_DIR") ?: "${projectDir}/lib"
    systemProperty("java.library.path", libDir)
    systemProperty("jna.library.path", libDir)
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}
