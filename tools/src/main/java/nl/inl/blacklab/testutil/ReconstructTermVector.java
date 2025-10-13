package nl.inl.blacklab.testutil;

import java.nio.file.Paths;

import org.apache.commons.text.WordUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import nl.inl.util.LuceneUtil;

public class ReconstructTermVector {

    private ReconstructTermVector() {
    }

    public static void main(String[] args) {
        int docId = 0;
        String fieldName = "contents%word@s";
        int first = 0;
        int number = 100;
        try {
            if (args.length >= 1)
                docId = Integer.parseInt(args[0]);
            if (args.length >= 2)
                first = Integer.parseInt(args[1]);
            if (args.length >= 3)
                number = Integer.parseInt(args[2]);
            if (args.length >= 4)
                fieldName = args[3];
        } catch (NumberFormatException e) {
            System.err.println("Wrong number format.");
            usage();
            System.exit(1);
        }

        // Open the index
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(".")));
        } catch (Exception e) {
            System.err.println("Error opening index; is the current directory a Lucene index?");
            usage();
            System.exit(1);
        }

        // Get words from the term vector
        String[] words = LuceneUtil.getWordsFromTermVector(reader, docId, fieldName, first, first + number, true);

        // Print result
        int i = 0;
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            if (word == null)
                word = "[MISSING]";
            b.append(i).append(":").append(word).append(" ");
            i++;
        }
        System.out.println(WordUtils.wrap(b.toString(), 80));
    }

    private static void usage() {
        System.out.println("\nThis tool reconstructs the term vector for a document. Run it from the index dir.\n");
        System.out.println("  ReconstructTermVector <docId> [first] [number] [fieldName]");
    }

}
