import Foundation

private let chrondbVersion = "0.2.3"

private var libName: String {
    #if os(macOS)
    return "libchrondb.dylib"
    #elseif os(Linux)
    return "libchrondb.so"
    #else
    return "libchrondb.so"
    #endif
}

private var platformTag: String? {
    #if os(Linux) && arch(x86_64)
    return "linux-x86_64"
    #elseif os(Linux) && arch(arm64)
    return "linux-aarch64"
    #elseif os(macOS) && arch(x86_64)
    return "macos-x86_64"
    #elseif os(macOS) && arch(arm64)
    return "macos-aarch64"
    #else
    return nil
    #endif
}

private var homeLibDir: String {
    let home = FileManager.default.homeDirectoryForCurrentUser.path
    return "\(home)/.chrondb/lib"
}

/// Ensures the native library is installed, downloading if necessary.
/// Returns the directory containing the library.
@discardableResult
public func ensureChronDBLibraryInstalled() throws -> String {
    // Priority 1: CHRONDB_LIB_DIR env var
    if let envDir = ProcessInfo.processInfo.environment["CHRONDB_LIB_DIR"] {
        let envPath = "\(envDir)/\(libName)"
        if FileManager.default.fileExists(atPath: envPath) {
            return envDir
        }
    }

    // Priority 2: ~/.chrondb/lib/
    let homePath = "\(homeLibDir)/\(libName)"
    if FileManager.default.fileExists(atPath: homePath) {
        return homeLibDir
    }

    // Priority 3: Auto-download
    try downloadLibrary()

    if FileManager.default.fileExists(atPath: homePath) {
        return homeLibDir
    }

    throw ChronDBError.setupFailed(
        "Library \"\(libName)\" not found. Set CHRONDB_LIB_DIR or install to ~/.chrondb/lib/"
    )
}

private func downloadLibrary() throws {
    guard let platform = platformTag else {
        throw ChronDBError.setupFailed("No pre-built library available for this platform")
    }

    let releaseTag = chrondbVersion.contains("-dev") ? "latest" : "v\(chrondbVersion)"
    let versionLabel = chrondbVersion.contains("-dev") ? "latest" : chrondbVersion
    let url = "https://github.com/avelino/chrondb/releases/download/\(releaseTag)/libchrondb-\(versionLabel)-\(platform).tar.gz"

    FileHandle.standardError.write("[chrondb] Native library not found, downloading...\n".data(using: .utf8)!)
    FileHandle.standardError.write("[chrondb] URL: \(url)\n".data(using: .utf8)!)
    FileHandle.standardError.write("[chrondb] Installing to: \(homeLibDir)\n".data(using: .utf8)!)

    try FileManager.default.createDirectory(atPath: homeLibDir, withIntermediateDirectories: true)

    let tempFile = NSTemporaryDirectory() + "chrondb-download-\(ProcessInfo.processInfo.processIdentifier).tar.gz"
    defer { try? FileManager.default.removeItem(atPath: tempFile) }

    // Download using curl
    let download = Process()
    download.executableURL = URL(fileURLWithPath: "/usr/bin/curl")
    download.arguments = ["-fSL", "-o", tempFile, url]
    try download.run()
    download.waitUntilExit()

    guard download.terminationStatus == 0 else {
        throw ChronDBError.setupFailed("Download failed (curl exit code: \(download.terminationStatus))")
    }

    // Extract lib/ contents
    let extract = Process()
    extract.executableURL = URL(fileURLWithPath: "/usr/bin/tar")
    extract.arguments = ["xzf", tempFile, "-C", homeLibDir, "--strip-components=2", "--include=*/lib/*"]
    try extract.run()
    extract.waitUntilExit()

    // Also extract include/
    let extractHeaders = Process()
    extractHeaders.executableURL = URL(fileURLWithPath: "/usr/bin/tar")
    extractHeaders.arguments = ["xzf", tempFile, "-C", homeLibDir, "--strip-components=2", "--include=*/include/*"]
    try? extractHeaders.run()
    extractHeaders.waitUntilExit()

    guard FileManager.default.fileExists(atPath: "\(homeLibDir)/\(libName)") else {
        throw ChronDBError.setupFailed("Library not found after extraction")
    }

    FileHandle.standardError.write("[chrondb] Library installed successfully!\n".data(using: .utf8)!)
}
