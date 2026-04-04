/// Base exception for all ChronDB errors.
class ChronDBException implements Exception {
  final String message;
  ChronDBException(this.message);

  @override
  String toString() => 'ChronDBException: $message';
}

/// Thrown when a document is not found.
class DocumentNotFoundException extends ChronDBException {
  DocumentNotFoundException([String message = 'Document not found'])
      : super(message);

  @override
  String toString() => 'DocumentNotFoundException: $message';
}
