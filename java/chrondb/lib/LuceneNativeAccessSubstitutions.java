package chrondb.lib;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.util.Optional;

/**
 * Keeps Lucene's Panama libc binding out of the shared library.
 *
 * On JDK 21, Lucene 9.12 ships {@code PosixNativeAccess}, which looks up
 * {@code madvise} through the Foreign Function API in its static initializer.
 * GraalVM 21 cannot compile that lookup ({@code NativeLibrary.findEntry0} is
 * unsupported), so any reachable path to it fails the image build. The only
 * caller is {@code NativeAccess.getImplementation()}, used by the MemorySegment
 * mmap provider for read-ahead hints. Returning empty makes Lucene skip the
 * hints, which is the same behavior it has on platforms without the binding.
 * The image also sets {@code MMapDirectory.enableMemorySegments=false}, so the
 * MappedByteBuffer provider is used at run time.
 */
@TargetClass(className = "org.apache.lucene.store.NativeAccess")
final class Target_org_apache_lucene_store_NativeAccess {
    @Substitute
    static Optional<?> getImplementation() {
        return Optional.empty();
    }
}
