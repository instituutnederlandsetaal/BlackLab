package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.util.FileUtil;

public class IndexManager {
    private static final Logger logger = LogManager.getLogger(IndexManager.class);

    /**
     * A file by this name is placed in user directories that could not be fully
     * deleted, This can happen under windows when some of the files are still open
     * in some memory-maps (despite .close() having been called) The files cannot be
     * deleted until the streams and files are garbage-collected. Since there is no
     * deterministic way to trigger a gc run, we detect this situation and mark the
     * directory for future deletion.
     */
    private static final String PENDING_DELETION_FILE_MARKER = ".markedfordeletion";

    /**
     * The frequency at which we check for removed indices in the file system.
     */
    private static final int REMOVED_INDICES_MONITOR_CHECK_IN_MS = 1000;

    /** File in user dir containing the original user id */
    public static final String USER_ID_FILE_NAME = ".userId";

    private final SearchManager searchMan;

    /** Configured index collections directories */
    private final List<File> collectionsDirs;

    /**
     * Logged-in users will have their own private collections dir. This is the
     * parent of those dirs.
     */
    private File userCollectionsDir;

    /**
     * Manages the loaded user document formats and exposes them to BlackLab-core
     * for use.
     */
    private FinderInputFormatUserFormats userFormatManager;

    private final Map<String, Index> indices = new HashMap<>();

    /** Did we (attempt to) load all user corpora? */
    private boolean allUserCorporaLoaded;

    public IndexManager(SearchManager searchMan, BLSConfig blsConfig) throws ConfigurationException {
        this.searchMan = searchMan;

        // List of index collections dirs (and/or single index dirs)
        List<String> indexes = blsConfig.getIndexLocations();
        collectionsDirs = new ArrayList<>();
        for (String indexPath: indexes) {
            File indexDir = new File(indexPath);
            if (!indexDir.exists())
                throw new ConfigurationException("Directory in indexLocations doesn't exist (or parent dir not accessible): " + indexDir);
            if (!indexDir.canRead())
                throw new ConfigurationException("Directory in indexLocations cannot be read (check permissions): " + indexDir);

            // Is this a single index, or a collection of indexes?
            if (BlackLabIndex.isIndex(indexDir)) {
                // Single index.
                logger.debug("Single index found: " + indexDir);
                Index index;
                try {
                    index = new Index(indexDir.getName(), indexDir, searchMan);
                    indices.put(indexDir.getName(), index);
                } catch (FileNotFoundException | IllegalIndexName e) {
                    logger.error("Error opening index '" + indexDir + "'; " + e.getMessage());
                }
            } else {
                // Collection of indices, probably..?
                logger.debug("Index collection dir found: " + indexDir);
                collectionsDirs.add(indexDir);
            }
        }

        // User collections dir; these are like collections, but within a user's directory
        userCollectionsDir = null;
        if (!StringUtils.isEmpty(blsConfig.getUserIndexes())) {
            File userIndexesDir = new File(blsConfig.getUserIndexes());
            if (!userIndexesDir.exists())
                logger.warn("Configured user collections does not exist: " + userIndexesDir);
            else if (!userIndexesDir.canRead())
                logger.warn("Configured user collections unreadable: " + userIndexesDir);
            else {
                userCollectionsDir = userIndexesDir;
                userFormatManager = new FinderInputFormatUserFormats(userCollectionsDir);
                DocumentFormats.addFinder(userFormatManager);
            }
        }

        if (!blsConfig.isSolr())
            checkAnyIndexesAvailable();
        List<File> allDirs = new ArrayList<>(collectionsDirs);
        // Since userCollectionsDir is initialized as null, and might still be null here, check for nullity
        if (userCollectionsDir != null)
            allDirs.add(userCollectionsDir);
        try {
            startRemovedIndicesMonitor(allDirs, REMOVED_INDICES_MONITOR_CHECK_IN_MS);
        } catch (Exception ex) {
            throw BlackLabRuntimeException.wrap(ex);
        }
    }

    private void checkAnyIndexesAvailable() throws ConfigurationException {
        if (indices.isEmpty() && collectionsDirs.isEmpty() && userCollectionsDir == null) {
            throw new ConfigurationException(
                    "Configuration error: no readable indexLocations found. Make sure blacklab-server.yaml contains " +
                            "indexLocations or userIndexes (or both) settings. See "
                            + "https://blacklab.ivdnt.org/server/configuration.html#minimal-config-file for a "
                            + "minimal configuration file.");
        }
    }

    /**
     * Return the specified user's collection dir.
     *
     * @param user the user
     * @return the user's collection dir, or null if it can't be read or created
     */
    private File getUserCollectionDir(User user) {
        assert user != null;
        if (userCollectionsDir == null || !user.isLoggedIn() || user.getId().isEmpty())
            return null;
        File dir = new File(userCollectionsDir, user.getUserDirName());
        if (!dir.exists())
            dir.mkdir();
        if (!dir.canRead()) {
            logger.error("Cannot read collections dir for user: " + dir);
            logger.error("(userCollectionsDir = " + userCollectionsDir);
            return null;
        }

        // Make sure the user id is saved in the user directory.
        // (the directory name cannot be used because it is mangled to be safe and unique)
        File userIdFile = new File(dir, USER_ID_FILE_NAME);
        try {
            if (userIdFile.exists()) {
                String readUserId = FileUtils.readFileToString(userIdFile, StandardCharsets.UTF_8).trim();
                assert user.getId().equals(readUserId);
            } else {
                FileUtils.writeStringToFile(userIdFile, user.getId(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        return dir;
    }

    /**
     * Does the specified index exist? Attempts to load any new public indices
     * before returning (if this is a user index, attempts to load any new indices
     * for this user).
     *
     * @param indexId the index we want to check for
     * @return true iff the index exists
     */
    public synchronized boolean indexExists(String indexId) throws BlsException {
        try {
            if (!indices.containsKey(indexId)) {
                if (Index.isUserIndex(indexId))
                    loadUserIndices(User.fromId(Index.getUserId(indexId)));
                else
                    loadPublicIndices();
            }
            return indices.containsKey(indexId);
        } catch (IllegalIndexName e) {
            throw new IndexNotFound(e.getMessage());
        }
    }

    /**
     * Create an empty user index.
     *
     * Indices may only be created by a logged-in user in his own private area. The
     * index name is strictly validated, disallowing any weird input.
     *
     * @param user the logged-in user
     * @param indexId the index name, including user prefix
     * @param formatIdentifier the document format identifier (e.g. tei, folia, ..).
     *            See {@link DocumentFormats}
     * @throws BlsException if we're not allowed to create the index for whatever
     *             reason
     * @throws ErrorOpeningIndex if creation failed unexpectedly
     */
    public synchronized void createIndex(User user, String indexId, String displayName, String formatIdentifier)
            throws BlsException, ErrorOpeningIndex {
        if (!DocumentFormats.isSupported(formatIdentifier))
            throw new BadRequest("FORMAT_NOT_FOUND", "Unknown format: " + formatIdentifier);
        if (!Index.isUserIndex(indexId))
            throw new NotAuthorized("You are trying to create a global (non-private) corpus (" + indexId + "). Only private corpora can be created via BLS.");
        if (!Index.isValidIndexName(indexId))
            throw new IllegalIndexName(indexId);
        if (indexExists(indexId))
            throw new BadRequest("INDEX_ALREADY_EXISTS",
                    "Could not create index. Index already exists.");

        User indexOwner = Index.getUser(indexId);
        if (!indexOwner.getId().equals(user.getId()) && !user.isSuperuser())
            throw new NotAuthorized("You (" + user.getId() + ") are not allowed to create a corpus for another user (" + indexId + ")");
        String indexName = Index.getIndexName(indexId);

        if (userCollectionsDir == null)
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                "Could not create index. The server is not configured with support for user content. See https://blacklab.ivdnt.org/server/howtos.html#let-users-manage-their-own-corpora");

        int maxNumberOfIndices = searchMan.config().getIndexing().getMaxNumberOfIndicesPerUser();
        if (!canCreateIndex(user))
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                    "Could not create index. You already have the maximum of "
                            + maxNumberOfIndices + " indices.");


        File userDir = getUserCollectionDir(indexOwner);
        if (userDir == null || !userDir.canWrite())
            throw new InternalServerError("Could not create index. Cannot write in user dir: " + userDir, "CANNOT_WRITE_USER_DIR");

        File indexDir = new File(userDir, indexName);

        // TODO this should be handled by Index
        if (isPendingDeletion(indexDir)) {
            // Don't let any deletion markers linger around (when index used to exist and couldn't be fully deleted)
            BlsUtils.delTree(indexDir);
        }
        boolean contentViewable = true; // user may view his own private corpus documents
        InputFormat inputFormat = DocumentFormats.getFormat(formatIdentifier).orElse(null);
        ConfigInputFormat config = inputFormat.isConfigurationBased() ? inputFormat.getConfig() : null;
        try (BlackLabIndexWriter indexWriter = searchMan.blackLabInstance().create(indexDir, config)) {
            IndexMetadataWriter indexMetadata = indexWriter.metadata();
            if (!StringUtils.isEmpty(displayName))
                indexMetadata.setDisplayName(displayName);
            if (config != null && config.getName() != null)
                indexMetadata.setDocumentFormat(config.getName());
            else if (!StringUtils.isEmpty(formatIdentifier)) {
                indexMetadata.setDocumentFormat(formatIdentifier);
            }
            indexMetadata.setContentViewable(contentViewable);
            indexMetadata.save();
        }

        try {
            logger.debug("Created index: " + indexName + " (" + indexDir + ")");
            indices.put(indexId, new Index(indexId, indexDir, this.searchMan));
        } catch (FileNotFoundException e) {
            throw new ErrorOpeningIndex("Could not open index: " + indexDir, e);
        }
    }

    public void registerIndex(String indexId, BlackLabIndex index) {
        try {
            indices.put(indexId, new Index(indexId, index, this.searchMan));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public boolean canCreateIndex(User user) {
        if (!user.isLoggedIn())
            return false;

        int maxNumberOfIndices = searchMan.config().getIndexing().getMaxNumberOfIndicesPerUser();

        // No limit on the number of indices
        if (maxNumberOfIndices ==  -1) {
            return true;
        }
        return userCollectionsDir != null &&
            (getAvailablePrivateCorporaOwnedBy(user).size() <= maxNumberOfIndices || user.isSuperuser());
    }

    /**
     * Delete a user index.
     *
     * Only user indices are deletable. The owner must be logged in. The index name
     * is strictly validated, disallowing any weird input. Many other checks are
     * done to root out all kinds of special cases.
     *
     * @param indexId the index id, including user prefix
     * @throws NotAuthorized if this is not a user index
     * @throws IndexNotFound if no such index exists
     * @throws InternalServerError if the index is in an invalid state
     */
    public synchronized void deleteUserIndex(String indexId)
            throws NotAuthorized, IndexNotFound, InternalServerError, IllegalIndexName {
        if (!Index.isUserIndex(indexId))
            throw new NotAuthorized("Deleting a global (non-private) corpus is not allowed. You can only delete your own private corpora.");

        Index index = getIndex(indexId);

        File indexDir = index.getDir();
        File userDir = getUserCollectionDir(index.getUser());

        // Generally these should never happen as they would have been triggered when the Index was first loaded
        // But, it can't hurt to be certain
        if (!indexDir.isDirectory())
            throw new InternalServerError("Could not delete index. Not an index.", "INTERR_DELETING_INDEX1");
        if (!userDir.canWrite() || !indexDir.canWrite())
            throw new InternalServerError("Could not delete index. Check file permissions.", "INTERR_DELETING_INDEX2");
        if (!indexDir.getAbsoluteFile().getParentFile().equals(userDir)) // Yes, we're paranoid..
            throw new InternalServerError("Could not delete index. Not found in user dir.", "INTERR_DELETING_INDEX3");
        if (!BlackLabIndex.isIndex(indexDir)) { // ..but are we paranoid enough?
            throw new InternalServerError("Could not delete index. Not a BlackLab index.", "INTERR_DELETING_INDEX4");
        }

        // Don't follow symlinks
        if (FileUtils.isSymlink(indexDir)) {
            throw new InternalServerError("Could not delete index. Is a symlink.", "INTERR_DELETING_INDEX5");
        }

        // Can we even delete the whole tree? If not, don't even try.
        try {
            FileUtil.processTree(indexDir, new FileUtil.FileTask() {
                @Override
                public void process(File f) {
                    if (!f.canWrite())
                        throw new RuntimeException("Cannot delete " + f);
                }
            });
        } catch (Exception e) {
            throw new InternalServerError("Could not delete index. Can't delete all files/dirs.", "INTERR_DELETING_INDEX7");
        }

        // Everything seems ok. Delete the index.
        logger.debug("Deleting user index " + index.getId());
        indices.remove(indexId);
        index.close();

        // Cant guarantee this will work, but may as well try
        System.gc();
        System.runFinalization();

        BlsUtils.delTree(indexDir);
        // didn't fully delete, this can happen under windows when some memmapped buffers haven't been gc'd yet
        // This is a system bug, not something we can do anything about, the gc first needs to clean up all references to those maps
        // Mark the directory and attempt to delete it next time we come across it
        if (indexDir.canRead())
            markForDeletion(indexDir);
    }

    /**
     * Get the Index with this id. Attempts to load public indices (if this index is
     * a user index, additionally tries to load the user's indices).
     *
     * @return the Index, never null
     * @throws IndexNotFound when the index could not be found
     */
    public synchronized Index getIndex(String indexId) throws IndexNotFound {
        try {
            if (!indices.containsKey(indexId)) {
                if (Index.isUserIndex(indexId))
                    loadUserIndices(Index.getUser(indexId));
                else
                    loadPublicIndices();
            }

            Index index = indices.get(indexId);
            if (index == null)
                throw new IndexNotFound(indexId);

            return index;
        } catch (IllegalIndexName e) {
            throw new IndexNotFound(e.getMessage());
        }
    }

    /**
     * Return all loaded corpora, regardless of whether we can access them or not.
     *
     * Note that this will not return corpora shared with you if they haven't been
     * loaded yet. To fix this, we should probably find all corpora and which users
     * they're shared with on startup, but not open them until they're actually used.
     *
     * @return list of loaded corpora
     */
    public synchronized Collection<Index> getAllLoadedCorpora() {
        // Note that
        return indices.values();
    }

    /**
     * Get all public indices plus all indices owned by this user. Attempts to load
     * any new public indices and indices owned by this user.
     *
     * @param user the user
     * @return the list of indices
     */
    public synchronized List<Index> getAllAvailableCorpora(User user) {
        List<Index> availableIndices = new ArrayList<>();
        availableIndices.addAll(getAvailablePublicCorpora());
        availableIndices.addAll(getAvailablePrivateCorporaOwnedBy(user));
        availableIndices.addAll(getPrivateCorporaSharedWith(user));
        String ownPrefix = user.isLoggedIn() ? user.getId() + ":" : null;
        availableIndices.sort((o1, o2) -> {
            // Sort public before private
            boolean o1priv = o1.isUserIndex();
            boolean o2priv = o2.isUserIndex();
            if (o1priv != o2priv)
                return o1priv ? 1 : -1;

            // Sort own indexes before other's
            if (ownPrefix != null) {
                boolean isOwn1 = o1.getUserId() != null && o1.getUserId().startsWith(ownPrefix);
                boolean isOwn2 = o2.getUserId() != null && o2.getUserId().startsWith(ownPrefix);
                if (isOwn1 != isOwn2)
                    return isOwn1 ? -1 : 1;
            }

            // Sort rest case-insensitively
            return o1.getId().toLowerCase().compareTo(o2.getId().toLowerCase());
        });
        return availableIndices;
    }

    private List<Index> getPrivateCorporaSharedWith(User user) {
        return getAllLoadedCorpora().stream().filter(i -> i.sharedWith(user)).collect(Collectors.toList());
    }

    /**
     * Return the list of private indices available for searching. Attempts to load
     * any new indices for this user.
     *
     * @param user the user
     * @return the list of indices
     */
    public synchronized Collection<Index> getAvailablePrivateCorporaOwnedBy(User user) {
        assert user != null;
        if (!user.isLoggedIn())
            return Collections.emptyList();

        loadUserIndices(user);

        Set<Index> availableIndices = new HashSet<>();
        for (Index i : indices.values()) {
            if (user.getId().equals(i.getUserId()))
                availableIndices.add(i);
        }

        return availableIndices;
    }

    /**
     * Return the list of public indices available for searching. Attempts to load
     * any new public indices.
     *
     * @return the list of indices
     */
    public synchronized Collection<Index> getAvailablePublicCorpora() {
        Set<Index> availableIndices = new HashSet<>();

        loadPublicIndices();
        for (Index i: indices.values()) {
            if (!i.isUserIndex())
                availableIndices.add(i);
        }

        return availableIndices;
    }

    /**
     * Find all indices within our collection directories, and add them to the
     * {@link IndexManager#indices} list. Indices that are already loaded are
     * skipped.
     */
    private synchronized void loadPublicIndices() {
        if (collectionsDirs == null)
            return;

        logger.debug("Looking for indices in collectionsDirs...");
        for (File dir : collectionsDirs) {
            logger.debug("Scanning collectionsDir: " + dir);
            loadIndexesInDir(dir);
        }

        // Find all user corpora and keep track of them, so we can figure out ones shared with you.
        loadAllUserCorpora();
    }

    /** A file filter that accepts all directories (and files) except the userCollectionsDir,
     *  so if the userCollectionsDir is inside a collectionsDir, it is not suddenly made public
     */
    IOFileFilter notUserDirFilter = new IOFileFilter() {
        @Override
        public boolean accept(File pathName) {
            try {
                if (userCollectionsDir == null)
                    return true;
                return !pathName.getCanonicalPath().equals(userCollectionsDir.getCanonicalPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean accept(File pathName, String fileName) {
            return accept(new File(pathName, fileName));
        }
    };

    /** Accepts both directories and symlinks to directories */
    IOFileFilter symlinkToDirFilter = new IOFileFilter() {
        @Override
        public boolean accept(File pathName) {
            try {
                Path indexPath = pathName.toPath().toRealPath();
                return Files.isDirectory(indexPath);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }

        @Override
        public boolean accept(File pathName, String fileName) {
            return accept(new File(pathName, fileName));
        }
    };

    private synchronized void loadIndexesInDir(File dir) {
        for (File subDir: FileUtils.listFilesAndDirs(dir, symlinkToDirFilter, notUserDirFilter)) {
            // Follow symlinks
            Path indexPath;
            try {
                indexPath = subDir.toPath().toRealPath();
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
            if (!Files.isReadable(indexPath) || !BlackLabIndex.isIndex(indexPath)) {
                // Not readable or not an index.
                // Warn about this only if this directory is a direct subdir of a collection dir.
                // (otherwise we get warnings about all forward index directories)
                if (indexPath.toFile().getParentFile().equals(dir)) {
                    if (!Files.isReadable(indexPath))
                        logger.debug("  Cannot read dir: " + indexPath);
                    else
                        logger.debug("  No index found in dir: " + indexPath);
                }
                continue;
            }

            String indexName = subDir.getName();
            if (indices.containsKey(indexName)) {
                // Index was already loaded, or name collision
                File otherDir = indices.get(indexName).getDir();
                if (!otherDir.equals(subDir)) {
                    logger.warn("  Skipping subdir " + subDir + " because another index (" + otherDir + ") is named '" + indexName + "' as well.");
                }
                continue;
            }

            try {
                logger.debug("  Index found: " + indexName + " (" + subDir + ")");
                indices.put(indexName, new Index(indexName, subDir, searchMan));
            } catch (Exception e) {
                logger.info("Error while loading index " + indexName + " at location " + subDir + "; "
                        + e.getMessage());
            }
        }
    }

    /**
     * Find and add all indices within this user's private directory, and add them
     * to the {@link IndexManager#indices} list. Indices that are already loaded are
     * skipped.
     *
     * @param user the user for which to load indices
     */
    private synchronized void loadUserIndices(User user) {
        File userDir = getUserCollectionDir(user);
        if (userDir == null)
            return;

        loadUserCorporaInDir(user, userDir);
    }

    /**
     * Load all user corpora.
     *
     * Actually loads corpora for all users with a .userId file in the user directory,
     * but that should be all of them if they were created with a modern BlackLab version
     * (4.0 or higher). User corpora missed here will still be loaded on demand.
     */
    private synchronized void loadAllUserCorpora() {
        if (allUserCorporaLoaded || userCollectionsDir == null)
            return;
        allUserCorporaLoaded = true;
        for (File userDir: userCollectionsDir.listFiles(BlsUtils.readableDirFilter)) {
            File userIdFile = new File(userDir, USER_ID_FILE_NAME);
            if (userIdFile.exists() && userIdFile.canRead()) {
                try {
                    String userId = FileUtils.readFileToString(userIdFile, StandardCharsets.UTF_8).trim();
                    loadUserCorporaInDir(User.fromId(userId), userDir);
                } catch (IOException e) {
                    throw new BlackLabRuntimeException(e);
                }
            }
        }
    }

    private synchronized void loadUserCorporaInDir(User user, File userDir) {
        /*
         * User indices are stored as a flat list of directories inside the user's private directory like so:
         * 	userDir
         * 		indexDir1
         * 		indexDir2
         * 		...
         *
         * The name of the directory is the UNPREFIXED name of the index, so we need to take care to concatenate the userId and indexName
         * so the index can be recognised as a private index.
         */
        logger.debug("Scanning userDir: " + userDir);
        for (File f : userDir.listFiles(BlsUtils.readableDirFilter)) {
            if (isPendingDeletion(f)) {
                BlsUtils.delTree(f);
                if (f.canRead())
                    markForDeletion(f); // Deleting didn't work (yet)
                continue;
            }

            if (!f.canRead() || !BlackLabIndex.isIndex(f))
                continue;

            try {
                String indexId = Index.getIndexId(f.getName(), user.getId());
                if (indices.containsKey(indexId))
                    continue;

                logger.debug("User index found: " + indexId + " (" + f + ")");
                indices.put(indexId, new Index(indexId, f, searchMan));
            } catch (Exception e) {
                logger.info("Error while loading index " + f.getName() + " at location " + f + "; " + e.getMessage());
            }
        }
    }

    /**
     * Starts a monitor to remove references to indices whose physical file was removed
     * @param directories to monitor
     * @param pollingIntervalInMs how ofter to monitor the directories
     * @return the monitor
     */
    public FileAlterationMonitor startRemovedIndicesMonitor(List<File> directories, long pollingIntervalInMs) throws Exception {
        logger.info("Installing index removal watcher on: {}", directories);
        FileAlterationMonitor monitor = new FileAlterationMonitor(pollingIntervalInMs);
        List<FileAlterationObserver> observers = directories.stream()
            .map(FileAlterationObserver::new)
            .collect(Collectors.toList());
        FileAlterationListenerAdaptor listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onDirectoryDelete(File directory) {
                logger.info("Directory deleted: {}", directory.getAbsolutePath());
                synchronized (IndexManager.this) {
                    Optional<Index> indexToDelete = indices.values().stream()
                        .filter(i -> i.getDir().equals(directory))
                        .findFirst();
                    indexToDelete.ifPresent(i -> {
                        logger.info("Deleting index {}, {}", i.getId(), i.getDir().getAbsolutePath());
                        indices.remove(i.getId()).close();
                    });
                }
            }
        };
        observers.forEach(o -> {
            o.addListener(listener);
            monitor.addObserver(o);
        });
        monitor.start();
        return monitor;
    }

    private static void markForDeletion(File directory) {
        try {
            File deletionMarker = new File(directory, PENDING_DELETION_FILE_MARKER);
            if (!deletionMarker.createNewFile())
                logger.error("Cannot mark directory for future deletion (createNewFile failed): " + deletionMarker);
        } catch (IOException e) {
            logger.error("Cannot mark directory for future deletion: " + e.getMessage());
        }
    }

    private static boolean isPendingDeletion(File directory) {
        File deletionMarker = new File(directory, PENDING_DELETION_FILE_MARKER);
        return (deletionMarker.exists() && deletionMarker.canRead());
    }

    /**
     * Note that this will return null if no userCollectionsDir has been set
     * configured, as there is no place to store/read the formats.
     *
     * @return The user format manager/DocIndexerFactory.
     */
    public FinderInputFormatUserFormats getUserFormatManager() {
        return userFormatManager;
    }
}
