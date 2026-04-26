package com.docgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limits for composite template ZIP imports ({@code importFromZip}).
 * Bounds archive size, entry count, per-entry uncompressed size, total extracted size,
 * and optional compression ratio (zip-bomb mitigation) when central directory sizes are present.
 */
@Component
@ConfigurationProperties(prefix = "composite-import.zip")
public class CompositeZipImportProperties {

    /**
     * Maximum compressed bytes accepted for the upload stream (multipart file size).
     */
    private long maxArchiveBytes = 52_428_800L;

    /**
     * Maximum sum of uncompressed bytes read from all entries (defense in depth).
     */
    private long maxTotalUncompressedBytes = 104_857_600L;

    /**
     * Maximum number of non-directory ZIP entries.
     */
    private int maxEntryCount = 512;

    /**
     * Maximum uncompressed bytes per entry.
     */
    private long maxEntryBytes = 20_971_520L;

    /**
     * Reject entries where declared uncompressed size exceeds declared compressed size multiplied by this value.
     * Set to {@code 0} to disable ratio checks (not recommended).
     */
    private int maxUncompressedToCompressedRatio = 100;

    public long getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    public void setMaxArchiveBytes(long maxArchiveBytes) {
        this.maxArchiveBytes = maxArchiveBytes;
    }

    public long getMaxTotalUncompressedBytes() {
        return maxTotalUncompressedBytes;
    }

    public void setMaxTotalUncompressedBytes(long maxTotalUncompressedBytes) {
        this.maxTotalUncompressedBytes = maxTotalUncompressedBytes;
    }

    public int getMaxEntryCount() {
        return maxEntryCount;
    }

    public void setMaxEntryCount(int maxEntryCount) {
        this.maxEntryCount = maxEntryCount;
    }

    public long getMaxEntryBytes() {
        return maxEntryBytes;
    }

    public void setMaxEntryBytes(long maxEntryBytes) {
        this.maxEntryBytes = maxEntryBytes;
    }

    public int getMaxUncompressedToCompressedRatio() {
        return maxUncompressedToCompressedRatio;
    }

    public void setMaxUncompressedToCompressedRatio(int maxUncompressedToCompressedRatio) {
        this.maxUncompressedToCompressedRatio = maxUncompressedToCompressedRatio;
    }
}
