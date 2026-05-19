package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestDocInfo(BlackLabIndex index, String docPid, Collection<MetadataField> metadataToInclude) {
    public static RequestDocInfo fromParams(QueryParams qpar) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        return new RequestDocInfo(
                index,
                qpar.getDocPid(),
                WebserviceParams.getMetadataToInclude(index, qpar.getListMetadataValuesFor())
        );
    }
}
