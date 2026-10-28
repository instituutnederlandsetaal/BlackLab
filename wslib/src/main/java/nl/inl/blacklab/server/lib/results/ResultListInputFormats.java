package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.InputFormatInfo;
import nl.inl.blacklab.server.index.FinderInputFormatUserFormats;
import nl.inl.blacklab.server.index.FinderInputFormatUserFormats.IllegalUserFormatIdentifier;
import nl.inl.blacklab.server.index.IndexManager;

public class ResultListInputFormats {

    private final ResultUserInfo userInfo;

    private final List<InputFormatInfo> inputFormats;

    private final boolean debugMode;

    ResultListInputFormats(ResultUserInfo userInfo, IndexManager indexMan, boolean debugMode) {
        this.userInfo = userInfo;
        this.debugMode = debugMode;

        // List all available input formats
        if (userInfo.isLoggedIn() && indexMan.getUserFormatManager() != null) {
            // Make sure users's formats are loaded
            indexMan.getUserFormatManager().loadUserFormats(userInfo.getUserId(), null);
        }
        inputFormats = new ArrayList<>();
        for (InputFormatInfo inputFormat: DocumentFormats.getFormats()) {
            try {
                String userId = FinderInputFormatUserFormats.getUserIdFromFormatIdentifier(inputFormat.getIdentifier());
                // Other user's formats are not explicitly enumerated (but should still be considered public)
                if (!userId.equals(userInfo.getUserId()))
                    continue;
            } catch (IllegalUserFormatIdentifier e) {
                // Alright, it's evidently not a user format, that means it's public. List it.
            }
            inputFormats.add(inputFormat);
        }
    }

    public ResultUserInfo getUserInfo() {
        return userInfo;
    }

    public List<InputFormatInfo> getFormats() {
        return inputFormats;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}
