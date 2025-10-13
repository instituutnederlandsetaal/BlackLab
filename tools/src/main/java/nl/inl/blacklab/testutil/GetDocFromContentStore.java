package nl.inl.blacklab.testutil;

import java.io.File;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreExternal;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;

/**
 * Retrieves and displays a document from a BlackLab content store.
 */
public class GetDocFromContentStore {

    private GetDocFromContentStore() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex {

        if (args.length != 2) {
            System.err.println("Usage: GetDocFromContentStore <contentStoreDir> <docId>");
            return;
        }

        File csDir = new File(args[0]);
        int id = Integer.parseInt(args[1]);

        ContentStore cs = ContentStoreExternal.open(csDir, false, false);
        String content = cs.retrieve(id);
        System.out.println(content);
    }
}
