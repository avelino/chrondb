package com.chrondb

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.GZIPInputStream

internal object Setup {
    private const val VERSION = "0.2.3"
    private var installed = false

    private val libName: String
        get() = when {
            System.getProperty("os.name").lowercase().contains("mac") -> "libchrondb.dylib"
            System.getProperty("os.name").lowercase().contains("win") -> "chrondb.dll"
            else -> "libchrondb.so"
        }

    private val platformTag: String?
        get() {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            return when {
                os.contains("linux") && (arch == "amd64" || arch == "x86_64") -> "linux-x86_64"
                os.contains("linux") && (arch == "aarch64" || arch == "arm64") -> "linux-aarch64"
                os.contains("mac") && (arch == "amd64" || arch == "x86_64") -> "macos-x86_64"
                os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "macos-aarch64"
                else -> null
            }
        }

    private val homeLibDir: File
        get() = File(System.getProperty("user.home"), ".chrondb/lib")

    @Synchronized
    fun ensureLibraryInstalled() {
        if (installed) return

        if (libraryExists()) {
            installed = true
            return
        }

        downloadLibrary()
        installed = true
    }

    private fun libraryExists(): Boolean {
        // Check CHRONDB_LIB_DIR
        val envDir = System.getenv("CHRONDB_LIB_DIR")
        if (envDir != null && File(envDir, libName).exists()) return true

        // Check ~/.chrondb/lib/
        if (File(homeLibDir, libName).exists()) return true

        return false
    }

    private fun downloadLibrary() {
        val platform = platformTag
            ?: throw ChronDBException("No pre-built library available for this platform")

        val releaseTag = if (VERSION.contains("-dev")) "latest" else "v$VERSION"
        val versionLabel = if (VERSION.contains("-dev")) "latest" else VERSION
        val url = "https://github.com/avelino/chrondb/releases/download/$releaseTag/libchrondb-$versionLabel-$platform.tar.gz"

        System.err.println("[chrondb] Native library not found, downloading...")
        System.err.println("[chrondb] URL: $url")
        System.err.println("[chrondb] Installing to: $homeLibDir")

        homeLibDir.mkdirs()

        val tempFile = File.createTempFile("chrondb-download-", ".tar.gz")
        try {
            // Download
            URI(url).toURL().openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract using system tar (handles .tar.gz natively)
            val extractLib = ProcessBuilder(
                "tar", "xzf", tempFile.absolutePath,
                "-C", homeLibDir.absolutePath,
                "--strip-components=2",
                "--include=*/lib/*"
            ).start()
            extractLib.waitFor()

            // Also extract headers
            val extractHeaders = ProcessBuilder(
                "tar", "xzf", tempFile.absolutePath,
                "-C", homeLibDir.absolutePath,
                "--strip-components=2",
                "--include=*/include/*"
            ).start()
            extractHeaders.waitFor()

            if (!File(homeLibDir, libName).exists()) {
                throw ChronDBException("Library not found after extraction")
            }

            System.err.println("[chrondb] Library installed successfully!")
        } finally {
            tempFile.delete()
        }
    }
}
