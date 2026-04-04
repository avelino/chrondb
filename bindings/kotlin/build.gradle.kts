plugins {
    kotlin("jvm") version "1.9.22"
}

group = "run.avelino.chrondb"
version = file("../../VERSION").readText().trim()

repositories {
    mavenCentral()
}

dependencies {
    // ChronDB runs on the JVM — use Clojure interop directly, no FFI needed
    implementation("org.clojure:clojure:1.11.1")
    implementation("org.json:json:20240303")

    // ChronDB uberjar (added to classpath at runtime or via local file)
    implementation(files("../../target/chrondb.jar"))

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
        }
    }
}
