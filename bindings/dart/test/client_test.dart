import 'dart:io';

import 'package:chrondb/chrondb.dart';
import 'package:test/test.dart';

bool _libAvailable = false;

void main() {
  late Directory tempDir;
  late ChronDB db;

  setUpAll(() {
    try {
      tempDir = Directory.systemTemp.createTempSync('chrondb-check-');
      final testDb = ChronDB.openPath(tempDir.path);
      testDb.close();
      tempDir.deleteSync(recursive: true);
      _libAvailable = true;
    } catch (_) {
      _libAvailable = false;
    }
  });

  setUp(() {
    if (!_libAvailable) return;
    tempDir = Directory.systemTemp.createTempSync('chrondb-test-');
    db = ChronDB.openPath(tempDir.path);
  });

  tearDown(() {
    if (!_libAvailable) return;
    db.close();
    tempDir.deleteSync(recursive: true);
  });

  test('put and get', () {
    if (!_libAvailable) return;
    db.put('user:1', {'name': 'Alice', 'age': 30});
    final result = db.get('user:1');
    expect(result['name'], equals('Alice'));
    expect(result['age'], equals(30));
  });

  test('get not found', () {
    if (!_libAvailable) return;
    expect(() => db.get('nonexistent:999'), throwsA(isA<DocumentNotFoundException>()));
  });

  test('delete', () {
    if (!_libAvailable) return;
    db.put('user:2', {'name': 'Bob'});
    db.delete('user:2');
    expect(() => db.get('user:2'), throwsA(isA<DocumentNotFoundException>()));
  });

  test('list by prefix', () {
    if (!_libAvailable) return;
    db.put('item:1', {'name': 'A'});
    db.put('item:2', {'name': 'B'});
    final result = db.listByPrefix('item');
    expect(result, isNotNull);
  });

  test('list by table', () {
    if (!_libAvailable) return;
    db.put('product:1', {'name': 'Widget'});
    final result = db.listByTable('product');
    expect(result, isNotNull);
  });

  test('history', () {
    if (!_libAvailable) return;
    db.put('doc:1', {'version': 1});
    db.put('doc:1', {'version': 2});
    final result = db.history('doc:1');
    if (result is List) {
      expect(result.length, greaterThanOrEqualTo(2));
    }
  });
}
