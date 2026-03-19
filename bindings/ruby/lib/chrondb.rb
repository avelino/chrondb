# frozen_string_literal: true

require "json"
require_relative "chrondb_generated"

module ChronDB
  class Error < StandardError; end
  class DocumentNotFoundError < Error; end

  # A connection to a ChronDB database instance.
  #
  # @example Single-path usage (preferred)
  #   db = ChronDB::Client.new("/tmp/mydb")
  #   db.put("user:1", { name: "Alice" })
  #   doc = db.get("user:1")
  #
  # @example With idle timeout (long-running services)
  #   db = ChronDB::Client.new("/tmp/mydb", idle_timeout: 120)
  #
  class Client
    # Open a ChronDB database.
    #
    # @param db_path [String] Path for the database directory
    # @param index_path [String, nil] Deprecated. Separate index path.
    # @param idle_timeout [Integer, nil] Seconds of inactivity before suspending
    def initialize(db_path, index_path = nil, idle_timeout: nil)
      @inner = if index_path
                 warn "[DEPRECATION] Passing separate data_path and index_path is deprecated. " \
                      "Use ChronDB::Client.new(db_path) instead — the index is managed automatically.",
                      uplevel: 1
                 if idle_timeout
                   Chrondb::ChronDb.open_with_idle_timeout(db_path, index_path, idle_timeout)
                 else
                   Chrondb::ChronDb.open(db_path, index_path)
                 end
               else
                 if idle_timeout
                   idx = "#{db_path}/.chrondb-index"
                   Chrondb::ChronDb.open_with_idle_timeout(db_path, idx, idle_timeout)
                 else
                   Chrondb::ChronDb.open_path(db_path)
                 end
               end
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Save a document.
    #
    # @param id [String] Document ID (e.g., "user:1")
    # @param doc [Hash] Document data
    # @param branch [String, nil] Branch name
    # @return [Hash] The saved document
    def put(id, doc, branch: nil)
      result = @inner.put(id, JSON.generate(doc), branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError::NotFound => e
      raise DocumentNotFoundError, e.message
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Get a document by ID.
    #
    # @param id [String] Document ID
    # @param branch [String, nil] Branch name
    # @return [Hash] The document
    # @raise [DocumentNotFoundError] If not found
    def get(id, branch: nil)
      result = @inner.get(id, branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError::NotFound => e
      raise DocumentNotFoundError, e.message
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Delete a document by ID.
    #
    # @param id [String] Document ID
    # @param branch [String, nil] Branch name
    # @return [true]
    # @raise [DocumentNotFoundError] If not found
    def delete(id, branch: nil)
      @inner.delete(id, branch)
      true
    rescue Chrondb::ChronDbError::NotFound => e
      raise DocumentNotFoundError, e.message
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # List documents by ID prefix.
    #
    # @param prefix [String] ID prefix to match
    # @param branch [String, nil] Branch name
    # @return [Array<Hash>]
    def list_by_prefix(prefix, branch: nil)
      result = @inner.list_by_prefix(prefix, branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # List documents by table name.
    #
    # @param table [String] Table name
    # @param branch [String, nil] Branch name
    # @return [Array<Hash>]
    def list_by_table(table, branch: nil)
      result = @inner.list_by_table(table, branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Get the change history of a document.
    #
    # @param id [String] Document ID
    # @param branch [String, nil] Branch name
    # @return [Array<Hash>]
    def history(id, branch: nil)
      result = @inner.history(id, branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Execute a query against the Lucene index.
    #
    # @param query [Hash] Query in Lucene AST format
    # @param branch [String, nil] Branch name
    # @return [Hash]
    def query(query, branch: nil)
      result = @inner.query(JSON.generate(query), branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end

    # Execute a SQL query against the database.
    #
    # @param sql [String] SQL statement
    # @param branch [String, nil] Branch name
    # @return [Hash] Results depending on query type
    def execute(sql, branch: nil)
      result = @inner.execute_sql(sql, branch)
      JSON.parse(result)
    rescue Chrondb::ChronDbError => e
      raise Error, e.message
    end
  end
end
