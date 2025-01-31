# Plugins for converting/tagging

Convert and Tag plugins allow you to convert documents from formats like `.docx` or `.pdf` into an XML format, tokenize them and tag each word with annotations like lemma and part of speech.

- Create a class implementing `ConvertPlugin` or `TagPlugin`
- Make the class known to the java [SPI](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) system.  
  In short:
    - Create a jar containing your plugin class.
    - Add a file to the jar under `/META-INF/services/` with the name `nl.inl.blacklab.indexers.preprocess.ConvertPlugin` or `nl.inl.blacklab.indexers.preprocess.TagPlugin` depending on your plugin's type.
    - Add a single line containing your class's fully-qualified class name.
    - Add your jar to BlackLab's classpath.

Configuring your plugin is possible through `blacklab[-server].yaml`.  
Any options under `plugins.yourPluginId` will be passed to your plugin when it's initialized.

Example:

```yaml
# Plugin options. Plugins allow you to automatically convert files (e.g. .html, .docx) or
# apply linguistic tagging before indexing.
plugins:

  # Should we initialize plugins when they are first used?
  # (plugin initialization can take a while; during development, delayed initialization is
  # often convenient, but during production, you usually want to initialize right away)
  delayInitialization: false

  # Individual plugin configurations
  plugins:

    # Conversion plugin
    OpenConvert:
      jarPath: "/home/jan/int-projects/blacklab-data/autosearch-plugins/jars/OpenConvert-0.2.0.jar"

    # Tagging plugin
    DutchTagger:
      jarPath: "/home/jan/int-projects/blacklab-data/autosearch-plugins/jars/DutchTagger-0.2.0.jar"
      vectorFile:  "/home/jan/int-projects/blacklab-data/autosearch-plugins/tagger-data/sonar.vectors.bin"
      modelFile:   "/home/jan/int-projects/blacklab-data/autosearch-plugins/tagger-data/withMoreVectorrs"
      lexiconFile: "/home/jan/int-projects/blacklab-data/autosearch-plugins/tagger-data/spelling.tab"
```

If your plugin was loaded successfully it can now be used by adding the following to an import format (`.blf.yaml` file):

```yaml
tagPlugin: yourPluginId
convertPlugin: yourPluginId
```

**NOTE:** Even if you only use `convertPlugin`, you must still put `tagPlugin: noop` in your configuration to make the converters work. We will fix this technical limitation in a future version. 
