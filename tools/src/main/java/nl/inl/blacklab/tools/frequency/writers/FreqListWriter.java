package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.fory.Fory;
import org.apache.fory.config.Language;

import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.QuoteStrategies;
import net.jpountz.lz4.LZ4FrameOutputStream;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

abstract class FreqListWriter {
    static final Fory fory = getFory();

    static OutputStream prepareStream(File file, boolean compress) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        return compress ? new LZ4FrameOutputStream(fos) : fos;
    }

    static CsvWriter prepareCSVPrinter(File file, boolean compress) throws IOException {
        OutputStream stream = prepareStream(file, compress);
        Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        return CsvWriter.builder().fieldSeparator('\t').quoteStrategy(QuoteStrategies.EMPTY).build(w);
    }

    private static Fory getFory() {
        Fory fory = Fory.builder().withLanguage(Language.JAVA).requireClassRegistration(true).withAsyncCompilation(true)
                .withStringCompressed(true).build();
        fory.register(GroupIdHash.class);
        fory.register(OccurrenceCounts.class);
        return fory;
    }
}
