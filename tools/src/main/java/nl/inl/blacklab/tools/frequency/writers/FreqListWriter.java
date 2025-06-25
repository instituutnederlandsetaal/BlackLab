package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import net.jpountz.lz4.LZ4FrameInputStream;

import org.apache.fory.Fory;
import org.apache.fory.config.Language;

import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.QuoteStrategies;
import net.jpountz.lz4.LZ4FrameOutputStream;
import nl.inl.blacklab.tools.frequency.data.GroupIdHash;
import nl.inl.blacklab.tools.frequency.data.OccurrenceCounts;

import org.apache.fory.io.ForyInputStream;

abstract class FreqListWriter {
    static final Fory fory = getFory();

    static OutputStream getOutputStream(File file, boolean compress) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        return compress ? new LZ4FrameOutputStream(fos) : fos;
    }

    static CsvWriter getCsvWriter(File file, boolean compress) throws IOException {
        OutputStream stream = getOutputStream(file, compress);
        Writer w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        return CsvWriter.builder().fieldSeparator('\t').quoteStrategy(QuoteStrategies.EMPTY).build(w);
    }

    static ForyInputStream getForyInputStream(File file, boolean compress) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        InputStream zis = compress ? new LZ4FrameInputStream(fis) : fis;
        return new ForyInputStream(zis);
    }

    private static Fory getFory() {
        Fory fory = Fory.builder().withLanguage(Language.JAVA).requireClassRegistration(true).withAsyncCompilation(true)
                .withStringCompressed(true).build();
        fory.register(GroupIdHash.class);
        fory.register(OccurrenceCounts.class);
        return fory;
    }
}
