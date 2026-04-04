/// Dart/Flutter client for ChronDB.
///
/// ```dart
/// final db = ChronDB.openPath('/tmp/mydb');
/// db.put('user:1', {'name': 'Alice', 'age': 30});
/// final doc = db.get('user:1');
/// db.close();
/// ```
library chrondb;

export 'src/chrondb.dart' show ChronDB;
export 'src/errors.dart' show ChronDBException, DocumentNotFoundException;
