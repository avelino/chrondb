// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "ChronDB",
    platforms: [.macOS(.v12)],
    products: [
        .library(name: "ChronDB", targets: ["ChronDB"]),
    ],
    targets: [
        .systemLibrary(
            name: "ChronDBFFI",
            path: "Sources/ChronDBFFI"
        ),
        .target(
            name: "ChronDB",
            dependencies: ["ChronDBFFI"],
            path: "Sources/ChronDB"
        ),
        .testTarget(
            name: "ChronDBTests",
            dependencies: ["ChronDB"],
            path: "Tests/ChronDBTests"
        ),
    ]
)
