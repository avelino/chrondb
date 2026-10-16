plugins {
    kotlin("jvm") version "2.4.21"
}

group = "run.avelino.chrondb"
version = file("../../VERSION").readText().trim()

repositories {
    mavenCentral()
}

dependencies {
    // ChronDB runs on the JVM — use Clojure interop directly, no FFI needed
    implementation("org.clojure:clojure:1.12.6")
    implementation("org.json:json:20260814")

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
