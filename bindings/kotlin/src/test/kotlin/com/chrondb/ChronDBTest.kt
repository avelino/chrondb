package com.chrondb

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ChronDBTest {

    @TempDir
    lateinit var tempDir: Path

    private var db: ChronDB? = null
    private var libAvailable = false

    @BeforeEach
    fun setUp() {
        libAvailable = try {
            db = ChronDB.openPath(tempDir.resolve("testdb").toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    @AfterEach
    fun tearDown() {
        db?.close()
    }

    @Test
    fun testPutAndGet() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        val doc = mapOf("name" to "Alice", "age" to 30)
        db!!.put("user:1", doc)

        val result = db!!.get("user:1")
        assertEquals("Alice", result["name"])
        assertEquals(30, result["age"])
    }

    @Test
    fun testGetNotFound() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        assertThrows(DocumentNotFoundException::class.java) {
            db!!.get("nonexistent:999")
        }
    }

    @Test
    fun testDelete() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        db!!.put("user:2", mapOf("name" to "Bob"))
        db!!.delete("user:2")

        assertThrows(DocumentNotFoundException::class.java) {
            db!!.get("user:2")
        }
    }

    @Test
    fun testListByPrefix() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        db!!.put("item:1", mapOf("name" to "A"))
        db!!.put("item:2", mapOf("name" to "B"))
        db!!.put("other:1", mapOf("name" to "C"))

        val result = db!!.listByPrefix("item")
        assertNotNull(result)
    }

    @Test
    fun testListByTable() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        db!!.put("product:1", mapOf("name" to "Widget"))

        val result = db!!.listByTable("product")
        assertNotNull(result)
    }

    @Test
    fun testHistory() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        db!!.put("doc:1", mapOf("version" to 1))
        db!!.put("doc:1", mapOf("version" to 2))

        val result = db!!.history("doc:1")
        if (result is List<*>) {
            assertTrue(result.size >= 2)
        }
    }

    @Test
    fun testOpenDeprecated() {
        assumeTrue(libAvailable, "ChronDB shared library not available")
        val dir = tempDir.resolve("deprecated-db").toString()
        val idx = "$dir/.chrondb-index"
        @Suppress("DEPRECATION")
        val db2 = ChronDB.open(dir, idx)
        try {
            db2.put("test:1", mapOf("ok" to true))
            val doc = db2.get("test:1")
            assertEquals(true, doc["ok"])
        } finally {
            db2.close()
        }
    }
}
