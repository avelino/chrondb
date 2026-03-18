# frozen_string_literal: true

require "minitest/autorun"
require "tmpdir"
require_relative "../lib/chrondb"

# Skip all tests if shared library is not available
LIB_AVAILABLE = File.exist?(File.join(Dir.home, ".chrondb", "lib")) ||
                ENV["CHRONDB_LIB_PATH"]

class TestChronDB < Minitest::Test
  def setup
    skip "ChronDB shared library not available" unless LIB_AVAILABLE
    @dir = Dir.mktmpdir("chrondb-test")
    @db = ChronDB::Client.new(
      File.join(@dir, "data"),
      File.join(@dir, "index")
    )
  end

  def teardown
    FileUtils.rm_rf(@dir) if @dir
  end

  def test_put_and_get
    saved = @db.put("user:1", { "name" => "Alice", "age" => 30 })
    assert_equal "Alice", saved["name"]

    doc = @db.get("user:1")
    assert_equal "Alice", doc["name"]
  end

  def test_get_not_found
    assert_raises(ChronDB::DocumentNotFoundError) do
      @db.get("nonexistent:999")
    end
  end

  def test_delete
    @db.put("user:2", { "name" => "Bob" })
    assert @db.delete("user:2")

    assert_raises(ChronDB::DocumentNotFoundError) do
      @db.get("user:2")
    end
  end

  def test_list_by_prefix
    @db.put("user:1", { "name" => "Alice" })
    @db.put("user:2", { "name" => "Bob" })
    @db.put("product:1", { "name" => "Widget" })

    users = @db.list_by_prefix("user:")
    assert users.length >= 2
  end

  def test_list_by_table
    @db.put("user:1", { "name" => "Alice" })
    @db.put("user:2", { "name" => "Bob" })

    users = @db.list_by_table("user")
    assert users.length >= 2
  end

  def test_history
    @db.put("user:1", { "name" => "Alice", "version" => 1 })
    @db.put("user:1", { "name" => "Alice Updated", "version" => 2 })

    history = @db.history("user:1")
    assert history.length >= 1
  end

  def test_idle_timeout
    dir = Dir.mktmpdir("chrondb-idle")
    db = ChronDB::Client.new(
      File.join(dir, "data"),
      File.join(dir, "index"),
      idle_timeout: 60
    )

    db.put("idle:1", { "name" => "test" })
    doc = db.get("idle:1")
    assert_equal "test", doc["name"]
  ensure
    FileUtils.rm_rf(dir) if dir
  end
end
