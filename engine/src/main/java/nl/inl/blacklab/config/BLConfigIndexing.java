package nl.inl.blacklab.config;

import nl.inl.util.DownloadCache;

public class BLConfigIndexing {

    /** Max tokens for a private user index, default 100M **/
    private long userIndexMaxTokenCount = 100_000_000;

    boolean downloadAllowed = false;
    
    String downloadCacheDir = null;
    
    int downloadCacheSizeMegs = 100;
    
    int downloadCacheMaxFileSizeMegs = 100;
    
    int zipFilesMaxOpen = 10;
    
    int numberOfThreads = 2;

    int maxNumberOfIndicesPerUser = 10;

    /** Should inline tags and relations be indexed case- and accent-sensitive?
     * This used to be the default, but we've switched over to case-insensitive
     * indexing by default.
     */
    boolean relationsSensitive = false;

    /** Maximum length of a token value (i.e. a word), or 0 for no limit.
     * Note that Lucene has a hard limit of 32766 characters for a term;
     * values longer than that will cause an error.
     */
    int maxValueLength = 0;

    @Deprecated
    int maxMetadataValuesToStore = 0;

    public DownloadCache.Config downloadCacheConfig() {
        return new DownloadCache.Config() {
            @Override
            public boolean isDownloadAllowed() {
                return BLConfigIndexing.this.isDownloadAllowed();
            }

            @Override
            public String getDir() {
                return getDownloadCacheDir();
            }

            @Override
            public long getSize() {
                return getDownloadCacheSizeMegs() * 1_000_000L;
            }

            @Override
            public long getMaxFileSize() {
                return getDownloadCacheMaxFileSizeMegs() * 1_000_000L;
            }
        };
    }

    public boolean isDownloadAllowed() {
        return downloadAllowed;
    }

    @SuppressWarnings("unused")
    public void setDownloadAllowed(boolean downloadAllowed) {
        this.downloadAllowed = downloadAllowed;
    }

    public String getDownloadCacheDir() {
        return downloadCacheDir;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheDir(String downloadCacheDir) {
        this.downloadCacheDir = downloadCacheDir;
    }

    public int getDownloadCacheSizeMegs() {
        return downloadCacheSizeMegs;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheSizeMegs(int downloadCacheSizeMegs) {
        this.downloadCacheSizeMegs = downloadCacheSizeMegs;
    }

    public int getDownloadCacheMaxFileSizeMegs() {
        return downloadCacheMaxFileSizeMegs;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheMaxFileSizeMegs(int downloadCacheMaxFileSizeMegs) {
        this.downloadCacheMaxFileSizeMegs = downloadCacheMaxFileSizeMegs;
    }

    public int getZipFilesMaxOpen() {
        return zipFilesMaxOpen;
    }

    @SuppressWarnings("unused")
    public void setZipFilesMaxOpen(int zipFilesMaxOpen) {
        this.zipFilesMaxOpen = zipFilesMaxOpen;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    @SuppressWarnings("unused")
    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    public int getMaxNumberOfIndicesPerUser() {
        return maxNumberOfIndicesPerUser;
    }

    @SuppressWarnings("unused")
    public void setMaxNumberOfIndicesPerUser(int maxNumberOfIndicesPerUser) {
        this.maxNumberOfIndicesPerUser = maxNumberOfIndicesPerUser;
    }

    @SuppressWarnings("unused")
    public void setUserIndexMaxTokenCount(int maxTokenCount) {
        this.userIndexMaxTokenCount = maxTokenCount;
    }

    public long getUserIndexMaxTokenCount() {
        return this.userIndexMaxTokenCount;
    }

    public boolean isRelationsSensitive() {
        return relationsSensitive;
    }

    @SuppressWarnings("unused")
    public void setRelationsSensitive(boolean relationsSensitive) {
        this.relationsSensitive = relationsSensitive;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    @SuppressWarnings("unused")
    public void setMaxValueLength(int maxValueLength) {
        if (maxValueLength < 0) {
            throw new IllegalArgumentException("maxValueLength must be >= 0");
        }
        this.maxValueLength = maxValueLength;
    }

    public void setMaxMetadataValuesToStore(int maxMetadataValuesToStore) {
        this.maxMetadataValuesToStore = maxMetadataValuesToStore;
    }
}
