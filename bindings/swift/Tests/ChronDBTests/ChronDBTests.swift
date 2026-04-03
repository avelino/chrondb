import XCTest
@testable import ChronDB

final class ChronDBTests: XCTestCase {

    private var tempDir: String!
    private var db: ChronDBClient!

    private static var libAvailable: Bool = {
        do {
            let dir = NSTemporaryDirectory() + "chrondb-check-\(ProcessInfo.processInfo.globallyUniqueString)"
            _ = try ChronDBClient(dbPath: dir)
            try? FileManager.default.removeItem(atPath: dir)
            return true
        } catch {
            return false
        }
    }()

    override func setUp() {
        super.setUp()
        guard Self.libAvailable else { return }
        tempDir = NSTemporaryDirectory() + "chrondb-test-\(ProcessInfo.processInfo.globallyUniqueString)"
        db = try? ChronDBClient(dbPath: tempDir)
    }

    override func tearDown() {
        db = nil
        if let dir = tempDir {
            try? FileManager.default.removeItem(atPath: dir)
        }
        super.tearDown()
    }

    func testPutAndGet() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        let doc: [String: Any] = ["name": "Alice", "age": 30]
        try db.put(id: "user:1", doc: doc)

        let result = try db.get(id: "user:1")
        XCTAssertEqual(result["name"] as? String, "Alice")
        XCTAssertEqual(result["age"] as? Int, 30)
    }

    func testGetNotFound() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        XCTAssertThrowsError(try db.get(id: "nonexistent:999")) { error in
            guard case ChronDBError.notFound = error else {
                XCTFail("Expected ChronDBError.notFound, got \(error)")
                return
            }
        }
    }

    func testDelete() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        try db.put(id: "user:2", doc: ["name": "Bob"])
        try db.delete(id: "user:2")

        XCTAssertThrowsError(try db.get(id: "user:2")) { error in
            guard case ChronDBError.notFound = error else {
                XCTFail("Expected ChronDBError.notFound, got \(error)")
                return
            }
        }
    }

    func testListByPrefix() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        try db.put(id: "item:1", doc: ["name": "A"])
        try db.put(id: "item:2", doc: ["name": "B"])
        try db.put(id: "other:1", doc: ["name": "C"])

        let result = try db.listByPrefix(prefix: "item")
        XCTAssertNotNil(result)
    }

    func testListByTable() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        try db.put(id: "product:1", doc: ["name": "Widget"])
        let result = try db.listByTable(table: "product")
        XCTAssertNotNil(result)
    }

    func testHistory() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        try db.put(id: "doc:1", doc: ["version": 1])
        try db.put(id: "doc:1", doc: ["version": 2])

        let result = try db.history(id: "doc:1")
        if let array = result as? [Any] {
            XCTAssertGreaterThanOrEqual(array.count, 2)
        }
    }

    func testIdleTimeout() throws {
        try XCTSkipUnless(Self.libAvailable, "ChronDB shared library not available")

        let dir = NSTemporaryDirectory() + "chrondb-timeout-\(ProcessInfo.processInfo.globallyUniqueString)"
        defer { try? FileManager.default.removeItem(atPath: dir) }

        let timeoutDb = try ChronDBClient(
            dataPath: dir,
            indexPath: dir + "/.chrondb-index",
            idleTimeoutSecs: 120
        )
        try timeoutDb.put(id: "test:1", doc: ["ok": true])
        let doc = try timeoutDb.get(id: "test:1")
        XCTAssertEqual(doc["ok"] as? Bool, true)
    }
}
