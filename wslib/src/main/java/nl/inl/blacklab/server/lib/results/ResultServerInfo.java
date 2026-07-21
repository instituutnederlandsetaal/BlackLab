package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.RequestServerInfo;

public class ResultServerInfo {

    static final Logger logger = LogManager.getLogger(ResultServerInfo.class);

    private final boolean debugMode;

    private final boolean includeCustomInfo;

    private final ResultUserInfo userInfo;

    private final ResultListPlugins plugins;

    private final List<ResultIndexStatus> indexStatuses;

    public ResultServerInfo(RequestServerInfo request) {
        this.includeCustomInfo = request.includeCustomInfo();
        this.debugMode = request.debugMode();

        User user = request.user();
        IndexManager indexManager = request.indexManager();
        userInfo = new ResultUserInfo(user, indexManager);
        plugins = new ResultListPlugins();
        indexStatuses = new ArrayList<>();
        Collection<Index> indices = indexManager.getAllAvailableCorpora(user);
        for (Index index: indices) {
            try {
                indexStatuses.add(WebserviceOperations.resultIndexStatus(index));
            } catch (ErrorOpeningIndex e) {
                // Cannot open this index; log and skip it.
                logger.warn("Could not open index " + index.getId() + ": " + e.getMessage());
            }
        }
    }

    public boolean includeCustomInfo() {
        return includeCustomInfo;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ResultUserInfo getUserInfo() {
        return userInfo;
    }

    public ResultListPlugins getPlugins() {
        return plugins;
    }

    public List<ResultIndexStatus> getIndexStatuses() {
        return indexStatuses;
    }
}
