package nl.inl.blacklab.tools.frequency.writers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import org.apache.fory.Fory;
import org.apache.fory.config.Language;
import org.apache.fory.io.ForyInputStream;

import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.QuoteStrategies;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.data.BufferedForyInputStream;
import nl.inl.blacklab.tools.frequency.data.GroupId;

abstract class FreqListWriter {
    static final Fory fory = getFory();
    private static final CsvWriter.CsvWriterBuilder csvWriterBuilder = CsvWriter.builder()
            .fieldSeparator('\t')
            .quoteStrategy(QuoteStrategies.EMPTY);
    final FrequencyListConfig cfg;
    final AnnotationInfo aInfo;

    FreqListWriter(final FrequencyListConfig cfg, final AnnotationInfo aInfo) {
        this.cfg = cfg;
        this.aInfo = aInfo;
    }

    private static Fory getFory() {
        final var fory = Fory.builder().withLanguage(Language.JAVA)
                .requireClassRegistration(true)
                .withAsyncCompilation(true)
                .build();
        fory.register(GroupId.class);
        return fory;
    }

    final OutputStream getOutputStream(final File file) {
        try {
            final var fos = new FileOutputStream(file);
            return cfg.runConfig().compressed() ? new LZ4FrameOutputStream(fos) : fos;
        } catch (final IOException e) {
            throw reportIOException(e);
        }
    }

    final CsvWriter getCsvWriter(final File file) {
        final var stream = getOutputStream(file);
        final var w = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        return csvWriterBuilder.build(w);
    }

    final ForyInputStream getForyInputStream(final File file) {
        try {
            final var fis = new FileInputStream(file);
            final var zis = cfg.runConfig().compressed() ? new LZ4FrameInputStream(fis) : fis;
            return new BufferedForyInputStream(zis, 2 << 12);
        } catch (final IOException e) {
            throw reportIOException(e);
        }
    }

    final String getExt() {
        return cfg.runConfig().compressed() ? ".tsv.lz4" : ".tsv";
    }

    final RuntimeException reportIOException(final IOException e) {
        return new RuntimeException("Error writing output for " + cfg.name(), e);
    }
}
