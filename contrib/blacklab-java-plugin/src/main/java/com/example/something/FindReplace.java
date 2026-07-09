package com.example.something;

import java.io.IOException;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileReference;

/** Test plugin that will find a regex and replace it with a string.
 *
 * Takes two parameters:
 * - find (regex to find, required)
 * - replace (string to replace it with, optional defaults to "dog")
 */
public class FindReplace extends FileConverter {

    private static final Logger logger = LogManager.getLogger(FindReplace.class);

    private PluginParam parFind;

    private PluginParam parReplace;

    @Override
    public void initialize() throws PluginException {
        parFind = addParam(PString.any("find", true, 50));
        parReplace = addParam(PString.any("replace", false, 100));
        logger.info("initialize: find={}, replace={}", parFind, parReplace);
    }

    @Override
    public synchronized FileReference perform(FileReference input, String inputFormat, PluginParams params) throws PluginException {
        String find = params.getString(parFind).orElseThrow();
        String replace = params.getString(parReplace, "dog");
        try (Reader reader = input.getSinglePassReader()) {
            String str = IOUtils.toString(reader);
            String result = str.replaceAll(find, replace);
            logger.info("perform: {} replacements done", str.equals(result) ? "no" : "one or more");
            return FileReference.fromCharArray(input.getPath(), result.toCharArray(), input.getAssociatedFile());
        } catch (IOException e) {
            throw new PluginException("Error in ExamplePlugin", e);
        }
    }
}
