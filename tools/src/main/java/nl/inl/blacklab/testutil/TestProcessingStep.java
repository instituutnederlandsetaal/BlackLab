package nl.inl.blacklab.testutil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.process.ProcessingStepReplace;
import nl.inl.util.Timer;

/** See if using streams for processing steps is efficient enough.
 *
 * Outcome: seems to be slightly faster. Memory usage unknown, but
 * probably similar.
 */
public class TestProcessingStep {
    private TestProcessingStep() {
    }

    public static void main(String[] args) {
        ProcessingStep script = getTestSteps();
        List<String> words = randomWords(10_000_000);

        // Process using the old method
        Timer t = new Timer();
        List<String> resultOld = new ArrayList<>();
        for (String word: words) {
            resultOld.addAll(processStringMultipleValues(word));
        }
        System.out.println("Processed " + words.size() + " words using old method in " + t.elapsed());

        // Process using the new method
        t.reset();
        List<String> resultNew = new ArrayList<>();
        resultNew.addAll(script.perform(words.stream(), null).collect(Collectors.toList()));
        System.out.println("Processed " + words.size() + " words using new method in " + t.elapsedDescription());

        // Compare results
        if (resultOld.size() != resultNew.size()) {
            System.out.println("Different number of results: " + resultOld.size() + " vs " + resultNew.size());
        } else {
            for (int i = 0; i < resultOld.size(); i++) {
                if (!resultOld.get(i).equals(resultNew.get(i))) {
                    System.out.println("Different result at index " + i + ": " + resultOld.get(i) + " vs " + resultNew.get(i));
                    break;
                }
            }
        }
    }

    private static final Pattern find = Pattern.compile("a");

    private static final Pattern separator = Pattern.compile("-");

    private static List<String> processStringMultipleValues(String input) {
        List<String> result = new ArrayList<>();
        result.add(input);
        for (int x = 0; x < 2; x++) {
            if (x == 0) {
                // replace a -> e
                for (int i = 0; i < result.size(); ++i) {
                    String afterReplace = opReplace(result.get(i), find, "e");
                    result.set(i, afterReplace);
                }
            } else {
                continue;
//                // split on -
//                List<String> r = new ArrayList<>();
//                for (String s: result) {
//                    r.addAll(opSplit(s, separator, true, -1));
//                }
//                result = r;
            }
        }
        return result;
    }

    private static String opReplace(String value, Pattern find, String replace) {
        return find.matcher(value).replaceAll(replace);
    }

    private static List<String> opSplit(String value, Pattern separator, boolean keepOriginal, int keepIndex) {
        String[] parts = separator.split(value, -1);
        if (keepIndex < 0) {
            if (!keepOriginal) {
                return Arrays.asList(parts);
            } else {
                List<String> r = new ArrayList<>();
                r.add(value);
                Collections.addAll(r, parts);
                return r;
            }
        } else {
            return List.of(keepIndex < parts.length ? parts[keepIndex] : "");
        }
    }

    private static ProcessingStep getTestSteps() {
        ProcessingStep replace = new ProcessingStepReplace("a", "e", "", "replaced");
        return replace;
        //ProcessingStep split = new ProcessingStepSplit("-", "", "both");
        //return new ProcessingStepMultiple(List.of(replace, split));
    }

    private static String randomWord() {
        int length = (int) (Math.random() * 10) + 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (Math.random() < 0.1)
                sb.append('-');
            sb.append((char) ('a' + Math.random() * 26));
        }
        return sb.toString();
    }

    private static List<String> randomWords(int length) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            result.add(randomWord());
        }
        return result;
    }
}
