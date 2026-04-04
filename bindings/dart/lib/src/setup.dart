import 'dart:io';

import 'package:path/path.dart' as p;

const _version = '0.2.3';

String get _libName {
  if (Platform.isMacOS) return 'libchrondb.dylib';
  if (Platform.isWindows) return 'chrondb.dll';
  return 'libchrondb.so';
}

String? get _platform {
  if (Platform.isLinux) return 'linux-x86_64';
  if (Platform.isMacOS) return 'macos-aarch64';
  return null;
}

String get _homeLibDir {
  final home = Platform.environment['HOME'] ?? Platform.environment['USERPROFILE'] ?? '';
  return p.join(home, '.chrondb', 'lib');
}

/// Find the library path, downloading if necessary.
String ensureLibraryInstalled() {
  // Priority 1: CHRONDB_LIB_DIR env var
  final envDir = Platform.environment['CHRONDB_LIB_DIR'];
  if (envDir != null) {
    final envPath = p.join(envDir, _libName);
    if (File(envPath).existsSync()) return envPath;
  }

  // Priority 2: ~/.chrondb/lib/
  final homePath = p.join(_homeLibDir, _libName);
  if (File(homePath).existsSync()) return homePath;

  // Priority 3: Auto-download
  _downloadLibrary();

  if (File(homePath).existsSync()) return homePath;

  throw Exception(
      'ChronDB library "$_libName" not found. '
      'Set CHRONDB_LIB_DIR or install to ~/.chrondb/lib/');
}

void _downloadLibrary() {
  final platform = _platform;
  if (platform == null) {
    throw Exception('No pre-built library available for this platform');
  }

  final releaseTag = _version.contains('-dev') ? 'latest' : 'v$_version';
  final versionLabel = _version.contains('-dev') ? 'latest' : _version;
  final url =
      'https://github.com/avelino/chrondb/releases/download/$releaseTag/libchrondb-$versionLabel-$platform.tar.gz';

  stderr.writeln('[chrondb] Native library not found, downloading...');
  stderr.writeln('[chrondb] URL: $url');
  stderr.writeln('[chrondb] Installing to: $_homeLibDir');

  Directory(_homeLibDir).createSync(recursive: true);

  final tempFile = p.join(Directory.systemTemp.path, 'chrondb-download-${pid}.tar.gz');

  // Download using curl (available on macOS and Linux)
  var result = Process.runSync('curl', [
    '-fSL', '--connect-timeout', '10', '--max-time', '60',
    '-o', tempFile, url,
  ]);
  if (result.exitCode != 0) {
    throw Exception('Download failed: ${result.stderr}');
  }

  // Extract lib/ contents to ~/.chrondb/lib/
  result = Process.runSync('tar', [
    'xzf', tempFile,
    '-C', _homeLibDir,
    '--strip-components=2',
    '--include=*/lib/*',
  ]);

  // Also extract include/ for headers
  Process.runSync('tar', [
    'xzf', tempFile,
    '-C', _homeLibDir,
    '--strip-components=2',
    '--include=*/include/*',
  ]);

  try {
    File(tempFile).deleteSync();
  } catch (_) {}

  if (result.exitCode != 0) {
    throw Exception('Failed to extract archive: ${result.stderr}');
  }

  stderr.writeln('[chrondb] Library installed successfully!');
}
