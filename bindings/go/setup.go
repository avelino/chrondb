package chrondb

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
)

const version = "0.2.3"

var setupOnce sync.Once
var setupErr error

func libName() string {
	switch runtime.GOOS {
	case "darwin":
		return "libchrondb.dylib"
	case "windows":
		return "chrondb.dll"
	default:
		return "libchrondb.so"
	}
}

func platformTag() string {
	os := runtime.GOOS
	arch := runtime.GOARCH
	switch {
	case os == "linux" && arch == "amd64":
		return "linux-x86_64"
	case os == "linux" && arch == "arm64":
		return "linux-aarch64"
	case os == "darwin" && arch == "amd64":
		return "macos-x86_64"
	case os == "darwin" && arch == "arm64":
		return "macos-aarch64"
	default:
		return ""
	}
}

func homeLibDir() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".chrondb", "lib")
}

// ensureLibraryInstalled checks for the native library and downloads it if missing.
func ensureLibraryInstalled() error {
	setupOnce.Do(func() {
		if libraryExists() {
			return
		}
		setupErr = downloadLibrary()
	})
	return setupErr
}

func libraryExists() bool {
	name := libName()

	// Check CHRONDB_LIB_DIR
	if dir := os.Getenv("CHRONDB_LIB_DIR"); dir != "" {
		if _, err := os.Stat(filepath.Join(dir, name)); err == nil {
			return true
		}
	}

	// Check ~/.chrondb/lib/
	if dir := homeLibDir(); dir != "" {
		if _, err := os.Stat(filepath.Join(dir, name)); err == nil {
			return true
		}
	}

	return false
}

func downloadLibrary() error {
	platform := platformTag()
	if platform == "" {
		return fmt.Errorf("no pre-built library available for %s/%s", runtime.GOOS, runtime.GOARCH)
	}

	libDir := homeLibDir()
	if libDir == "" {
		return fmt.Errorf("cannot determine home directory")
	}

	releaseTag := "v" + version
	versionLabel := version
	if strings.Contains(version, "-dev") {
		releaseTag = "latest"
		versionLabel = "latest"
	}

	url := fmt.Sprintf(
		"https://github.com/avelino/chrondb/releases/download/%s/libchrondb-%s-%s.tar.gz",
		releaseTag, versionLabel, platform,
	)

	fmt.Fprintf(os.Stderr, "[chrondb] Native library not found, downloading...\n")
	fmt.Fprintf(os.Stderr, "[chrondb] URL: %s\n", url)
	fmt.Fprintf(os.Stderr, "[chrondb] Installing to: %s\n", libDir)

	if err := os.MkdirAll(libDir, 0o755); err != nil {
		return fmt.Errorf("failed to create lib directory: %w", err)
	}

	// Download to temp file
	tmpFile, err := os.CreateTemp("", "chrondb-download-*.tar.gz")
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}
	defer os.Remove(tmpFile.Name())
	defer tmpFile.Close()

	resp, err := http.Get(url)
	if err != nil {
		return fmt.Errorf("download failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("download failed with status %d", resp.StatusCode)
	}

	if _, err := io.Copy(tmpFile, resp.Body); err != nil {
		return fmt.Errorf("failed to write download: %w", err)
	}
	tmpFile.Close()

	// Extract lib/ contents
	cmd := exec.Command("tar", "xzf", tmpFile.Name(),
		"-C", libDir,
		"--strip-components=2",
		"--include=*/lib/*",
	)
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("failed to extract archive: %s: %w", string(out), err)
	}

	// Also extract include/ for headers
	cmd = exec.Command("tar", "xzf", tmpFile.Name(),
		"-C", libDir,
		"--strip-components=2",
		"--include=*/include/*",
	)
	cmd.Run() // Best effort

	// Verify
	if _, err := os.Stat(filepath.Join(libDir, libName())); err != nil {
		return fmt.Errorf("library not found after extraction")
	}

	fmt.Fprintf(os.Stderr, "[chrondb] Library installed successfully!\n")
	return nil
}
