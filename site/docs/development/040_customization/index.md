# Plugins in BlackLab

You can extend BlackLab's functionality by creating plugins. The goal is to keep the core engine as generic as possible, while allowing users to customize it to their needs.

<!-- @include: ../../_from_v5.md -->

The plugin system got a major overhaul after `4.x`. This page describes the new system.
The previous plugin system was more limited and likely did not have many users.

## Plugin types

Plugins can be used to customize various aspects of BlackLab:

- a `FileConverter` converts documents before indexing (i.e. extract text from binary format or tag text with linguistic information) (previously we had separate `ConvertPlugin` and `TagPlugin`; this replaces both)
- an `IndexSourceType` fetches documents to be indexed from a custom source (e.g. a web service, a database, etc.)
- an `InputFormatType` defines a new document format for indexing (formerly called `DocIndexer`)
- a `ProcessingInstruction` provides additional processing during indexing (e.g. apply stemming, look up a value by id, etc.)
- a `QueryFunction` adds a new function to BCQL. You could use this to expand a simple function call to a complex, frequently-used BCQL query.
- a `DocTaskType` represents a task that can be performed on all documents in the index, e.g. to export metadata or determine a set of documents to remove
- a `QueryParserProvider` adds support for a query language
- an `AuthMethodProvider` interfaces with your authentication setup so BlackLab can determine the currently logged-in user

We may add more plugin types in the future, e.g. a `DocumentHighlighter` to highlights hits in the original document content (currently only works for XML documents) or a `FileFilter` to inspect file contents and decide if we actually want to index it (e.g. you could skip files with fewer than X words).

Note that BlackLab already provides many implementations of these plugin types; check if your use case isn't supported by an existing plugin before creating your own.


## Creating a plugin

There's two ways to implement a plugin: via a script or a `.jar` file. The simplest option is a script written in [Groovy](https://groovy-lang.org/) (a JVM language very similar to Java). This is just a text file that doesn't need to be compiled.

The other option is a `.jar` file containing one or more Java classes. This is a bit more complex, but allows you to use any JVM language (Java, Kotlin, Scala, etc.), use third-party libraries, etc.


## Installing/configuring a plugin

Place your plugin script or `.jar` file in the `$BLACKLAB_CONFIG_DIR/plugins/` directory. Let's assume your plugin id (class name or script file name) is `AmazingPlugin`.

If your plugin needs configuration, create a file named `AmazingPlugin.yaml` in the same `plugins/` directory. BlackLab will automatically read it an pass it to your plugin as a `Map<String, Object>`. From the `initialize()` method, you can access the configuration using either method like `cfgString(key, defaultValue)` or `fullConfig()` to get the full map.

If your plugin needs to read additional files, create a directory named `AmazingPlugin/` in the same `plugins/` directory. BlackLab will pass this directory to your plugin automatically. Call `pluginsDir()` from your plugin to get the path to this directory. This method will always return a `File` object, but the directory may not exist.


## Referring to plugins

A plugin can be referred to by:
- its simple or qualified class name (if not implemented as an anonymous class), e.g. `my.awesome.plugins.AmazingPlugin` or just `AmazingPlugin`
- for Groovy plugins, their script file name (without the `.groovy` extension), e.g. `amazing-plugin` for a script named `amazing-plugin.groovy`
- the return value of the `getId()` method (if overridden)

Places where you refer to a plugin include:
- in a input format config (`.blf.yaml`) file:
  - in the `converters:` list
  - in the `process:` section for a metadata field or annotation (e.g. `action: AmazingOperation`)
- in `blacklab[-server].yaml`, in the `plugins.plugins` section (see below)
- when running IndexTool to:
  - indicate what to index (e.g. `file:/path/to/my/files` or `AmazingFileFinder:123456`)
  - indicate what input format to use (e.g. `IndexTool create index input AmazingInputFormat`)
- In BCQL, e.g. `AmazingFunction([lemma="tiger"], "word")`
- in Java code, via `PluginManager.type(ThePluginType.class).get("AmazingPlugin")`


## Plugin parameters

Some plugins may need additional parameter(s). This is in addition to their expected inputs: a `FileConverter` _always_ gets a file as input, but _may_ need an additional parameter to control some aspect of the conversion.

For example, if a `FileConverter` performs part of speech tagging, it may need to know the language of the text to be tagged. It would take one additional parameter named `language`, which could be set to e.g. `English` or `Dutch`.

For security reasons, such parameters must always be declared in the plugin's `initialize` method, not only with the basic type (i.e. string, integer, etc.), but ideally with extra validation rules (string must not be too long and match a specific regular expression; integer must fall within a range; etc.). The stricter the rules, the less likely your plugin can be exploited by a malicious user.

```java
    private PluginParam parLanguage;

    public void initialize() {
        // Declare that we take an optional parameter named "language", which must be either "English" or "Dutch"
        parLanguage = addParam(PEnum.of("language", List.of("English", "Dutch")));
    }
```

then in the plugin's code (e.g. `FileConverter.perform()`), you can access the parameter value via e.g. `params.getString(parLanguage[, defaultLanguage])`:

```java
    public FileReference perform(FileReference input, String inputFormat, PluginParams params) {
        String language = params.getString(parLanguage, "English");
        //...
    }
```

These are the available parameter types and their creation methods:

- `PString`: a string
  - `any(name, isRequired=false, maxLength=256)`: any string
  - `identifier(name, isRequired=false, maxLength=256)`: a string consisting of letters, digits and underscore, where the first character must be a letter or underscore (regex `[\p{L}_][\p{L}0-9_\-]*`)
  - `matching(name, regex, isRequired=false, maxLength=256)`: a string matching the given regular expression
- `PEnum`: a string that must be one of a predefined set of values
  - `of(name, allowedValues, isRequired=false)`: a string that must be one of the given allowed values (allowedValues may be a list of strings or a Java enum class)
- `PBoolean`: a boolean value (`true` or `false`)
  - `optional()`: optional boolean
  - `required()`: required boolean
- `PInteger`/`PFloat`: a long integer value / floating point value
  - `any(name, isRequired=false)`: any integer
  - `nonnegative(name, isRequired=false)`: a non-negative integer (0 or higher)
  - `range(name, min, max, isRequired=false)`: an integer within the given range (inclusive)
- `PList` / `PIntegerRange` / `PStringStringMap`: a list of values / a range of integers / string-to-string map:
  - `optional(name, validator)`: optional integer range with a custom validator
  - `required(name, validator)`: required integer range with a custom validator
- `PQuery`/`PMatchInfo`: a query / match info object (only for `QueryFunction` plugins)
  - `optional(name)`: optional match info
  - `required(name)`: required match info

## Troubleshooting common issues

### Groovy: stack overflow

In Groovy, if you don't declare variables with `def`, Groovy will try to access a property of the same name instead. This can lead to infinite recursion and a stack overflow if you're inside a closure.

Always declare variables inside Groovy closures with `def` to avoid accidental property access and recursion.


## Server security and plugins

A carelessly written plugin could introduce a security vulnerability, e.g. by allowing users to read arbitrary files on the server or execute arbitrary code.

For this reason, BlackLab Server only allows some built-in plugins to run by default. The commandline tools like `IndexTool`, by contrast, will allow any plugin to run. If you want to use your own plugins with BlackLab Server, you need to explicitly allow them.

There two ways to allow a plugin to run from BlackLab Server:
- override the `isWebSafe()` method and have it return `true`, OR
- add the plugin id to the `plugins.allowed` list in `blacklab-server.yaml`

Especially if your plugin takes parameters (or other arbitrary user input such as files to be indexed), you should be extra careful to validate and sanitize this input to avoid vulnerabilities like code injection, path traversal, etc.

::: warning Security is your responsibility

Even if you're familiar with best practices for writing secure code, it's easy to introduce a vulnerability that may compromise your server. BlackLab itself probably has such vulnerabilities, even though we do our best to avoid them. Any plugins you create or use may have them as well.

It is up to you to mitigate the risks to your own satisfaction, e.g. by running your server in a secure environment, keeping your software up to date, validating plugin parameters, etc.

:::


## Examples

We'll now discuss examples for many of the plugin types. Most are implemented as Groovy scripts. For a `.jar` plugin, see the [`ProcessingInstruction`](#processinginstruction) example below.

### FileConverter example

For a simple `FileConverter` that adds a comment to the end of a TEI document, create a text file `$BLACKLAB_CONFIG_DIR/plugins/add-message.groovy` with the following content:

```groovy
import nl.inl.blacklab.plugins.FileConverter
import nl.inl.blacklab.plugins.param.PluginParams
import nl.inl.util.StringUtil
import nl.inl.util.fileprocessor.FileReference
import org.apache.commons.io.IOUtils

return new FileConverter() {
    String message

    FileReference perform(FileReference input, String format, PluginParams params) {
        try (def reader = input.getSinglePassReader()) {
            String str = IOUtils.toString(reader)
            System.err.println("Adding message to " + input.getPath())
            str = str.replaceAll("</TEI>", "<!-- " + StringUtil.escapeQuote(message, "'") + " --></TEI>")
            return FileReference.fromCharArray(input.getPath(), str.toCharArray(), input.getAssociatedFile())
        }
    }

    void initialize() {
        // Get the message from our YAML config file (or use default)
        message = cfgString("message", "Default groovy-plugin message")
        // If a message file exists in our config dir (plugins/, read the message from there instead
        def messageFile = cfgFileOptional("messageFile", "message.txt")
        if (messageFile.exists())
            message = IOUtils.toString(new FileReader(messageFile))
    }
}
```

This plugin can be used by adding the following to an import format (`.blf.yaml` file):

```yaml
# Apply conversion(s) before indexing
converters:
- id: add-message
  param1: value1   # (some plugins take parameters)
  param2: value2
```

Without configuration, a default message will be used. To customize it, create a file `$BLACKLAB_CONFIG_DIR/plugins/add-message.yaml` with the following content:

```yaml
message: "This document was processed by a groovy plugin"

# Alternatively, you can specify a file to read the message from.
# The file should be in this plugin's configuration directory, e.g.
# $BLACKLAB_CONFIG_DIR/plugins/add-message/message.txt
# (messageFile defaults to "message.txt")
# messageFile: message.txt
```

As you can see, this example has another way to configure the message, by reading it from a file in the plugin's (optional) configuration directory.


### IndexSourceType example

For a simple `IndexSourceType` that will index a test file, create a text file `$BLACKLAB_CONFIG_DIR/plugins/index-test.groovy` with the following content:

```groovy
import nl.inl.blacklab.index.IndexSource
import nl.inl.blacklab.plugins.IndexSourceType
import nl.inl.util.fileprocessor.FileIterator
import nl.inl.util.fileprocessor.FileReference

import java.util.stream.Collectors

return new IndexSourceType() {
    IndexSource get(String path, PluginParams params) {
        // A test "file" to index, with each word from path wrapped in <w> tags
        def content = "<TEI><text>" + Arrays.stream(path.split("\\s+", -1))
                .map(word -> "<w>" + word + "</w>")
                .collect(Collectors.joining("\n")) + "</text></TEI>";
        FileReference file = FileReference.fromCharArray("/test.xml", content.toCharArray(), null);

        return new IndexSource(path) {
            FileIterator filesToIndex() {
                return FileIterator.from(file, getFileIteratorSettings());
            }
        }
    }
}
```

This plugin can be used by running IndexTool like this:

```bash
IndexTool create index myindex "index-test:This is a test" tei-p5
```

It will create a TEI file in memory with the words "This is a test" wrapped in `<w>` tags, and index it using the `tei-p5` input format.

::: tip Skipping files

If you want to skip certain files from being indexed, have your `FileIterator.next()` method return 
`FileReference.DUMMY`. This can sometimes be a convenient way of e.g. skipping some files based on content. 

:::


### QueryFunction example

For a simple `QueryFunction` that matches a word and its reverse, create a text file `$BLACKLAB_CONFIG_DIR/plugins/wordOrReverse.groovy` with the following content:

```groovy
import nl.inl.blacklab.plugins.QueryFunction
import nl.inl.blacklab.plugins.ExprType
import nl.inl.blacklab.search.QueryExecutionContext
import nl.inl.blacklab.search.lucene.BLSpanQuery
import org.apache.lucene.index.Term
import org.apache.lucene.queries.spans.BLSpanOrQuery
import org.apache.lucene.queries.spans.SpanTermQuery

class QueryFunctionWordOrReverse extends QueryFunction {
    QueryFunctionWordOrReverse() {
        super("wordOrReverse", List.of(PString.any("value", true)));
    }

    BLSpanQuery term(QueryExecutionContext context, String field, String value) {
        return BLSpanQuery.wrap(context.queryInfo(), new SpanTermQuery(new Term(field, value)))
    }

    BLSpanQuery applyFunc(QueryExecutionContext context, List<Object> parameters) {
        String field = context.field().mainAnnotation().mainSensitivity().luceneField()
        String value = (String) parameters.get(0)
        BLSpanQuery a = term(context, field, value)
        BLSpanQuery b = term(context, field, value.reverse())
        return new BLSpanOrQuery(a, b)
    }
}
return new QueryFunctionWordOrReverse()
```

This plugin can be used in BCQL like this to find both `stressed` and `desserts`:

```
[wordOrReverse("stressed")]
```

or as a pseudo-annotation:

```
[wordOrReverse="stressed"]
```

Note that `QueryFunction.applyFunc()` is declared to return `TextPattern.EvalResult`. Valid types to return are:
- `BLSpanQuery` for span queries
- `MatchFilter` for the constraint part of the query (after `::`)
- `ConstraintValue` for simple values (string, integer, boolean, list)
- `Annotation` for an annotation in the corpus
- `QueryFunction` to return another function

The same types can be used for the parameters as well, declared in the constructor.

### DocTaskType example

For a simple `DocTaskType` that prints a document's persistent identifier (PID), create a text file `$BLACKLAB_CONFIG_DIR/plugins/printPid.groovy` with the following content:

```groovy
import nl.inl.blacklab.exceptions.PluginException
import nl.inl.blacklab.search.BlackLabIndex
import nl.inl.blacklab.search.BlackLabIndexWriter
import nl.inl.blacklab.search.DocTask
import nl.inl.blacklab.search.indexmetadata.MetadataField
import nl.inl.blacklab.plugins.DocTaskType
import org.apache.lucene.index.LeafReaderContext

// Prints the PID of each document in the index.
return new DocTaskType() {
    
    PluginParam parPrefix;
    
    void initialize() {
        parPrefix = addParam(PString.any("prefix"));
    }
    
    DocTask docTask(BlackLabIndex index, PluginParams params) {
        MetadataField metadataField = ((BlackLabIndexWriter) index).metadata().metadataFields().pidField()
        if (metadataField == null)
            throw new PluginException("Corpus has no configured pid field")
        String pidField = metadataField.name() // Name of this index' PID field.
        String prefix = params.getString(parPrefix, "PID: ")
        return (segment) -> (segmentDocId) -> {
            try {
                String pid = segment.reader().storedFields().document(segmentDocId, Set.of(pidField)).get(pidField)
                System.out.println(prefix + pid)
            } catch (IOException e) {
                throw new RuntimeException(e)
            }
        } as DocTask.SegmentTask
    }
}
```

Now, you can run this task on all documents in the index like this:

```bash
java nl.inl.blacklab.tools.IndexTool doctask ./my-index-dir printPid "prefix=Document PID: "
```

### ProcessingInstruction

As explained, a `.jar` plugin gives you the most flexibility, but are also a bit more complex. Read on for Groovy examples.

For a `.jar` plugin, you need to do the following:

- Create a class implementing the plugin type base class (e.g. `nl.inl.blacklab.plugins.FileConverter`)
- Make the class known to the java [SPI](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) system. In short:
    - Create a `.jar` containing your plugin class.
    - Add a text file to the `.jar` under `/META-INF/services/` with the name `nl.inl.blacklab.plugins.FileConverter` (or the correct type for your plugin). It should contain a single line with your class's fully-qualified class name (or multiple lines if your .jar contains multiple plugins).
- Place the `.jar` in `$BLACKLAB_CONFIG_DIR/plugins/`
- Optionally create a YAML config file and/or a subdirectory, both with the same name as your plugin (see above)

Let's create a `ProcessingInstruction` plugin that reverses a string. `ProcessingInstructionReverse.java` could look like this:

```java
package my.awesome.plugins;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.plugins.ProcessingInstruction;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;

public class ProcessingInstructionReverse extends ProcessingInstruction {
    @Override
    public synchronized String getId() {
        return "reverse";
    }
    
    @Override
    public ProcessingStep get(PluginParams params) {
        return new ProcessingStep() {
            @Override
            public String performSingle(String value, Map<String, List<String>> metadata) {
                return new StringBuilder(value).reverse().toString();
            }
            
            @Override
            public boolean canProduceMultipleValues() {
                // we don't split our input into multiple values
                return false;
            }
        };
    }
}
```

Compile the above to a `.jar` using e.g. Maven.

Make sure the `.jar` contains a file named `nl.inl.blacklab.plugins.ProcessingInstruction` under `/META-INF/services/` in your project, containing the single line:

```
my.awesome.plugins.ProcessingInstructionReverse
```

Place the `.jar` in `$BLACKLAB_CONFIG_DIR/plugins/`.

You can now use this plugin in your `.blf.yaml` file like this:

```yaml
metadata:
  containerPath: ./metadata
  fields:
  - name: normalAuthor
    valuePath: ./author
  - name: reversedAuthor
    valuePath: ./author
    process:
      action: reverse
```


### InputFormatType

InputFormatType is the most complex type of plugin, and you likely don't need it (we support XML, TSV, CoNNL-U and more out of the box, configurable with just a `.blf.yaml` file).

If you do need it, have a look at `InputFormatTypePlainText`, `InputFormatTypeTabular` and `InputFormatTypeXml` in the BlackLab source code for more complete examples. These are all configuration-based, so you can use them with `.blf.yaml` files.

If you decide to forego the flexibility of `.blf.yaml` support and try to index your format directly, have a look at `InputFormatTypeExample`, which is a toy example of a non-configuration-based format.

You are also always welcome to contact us for advice, via GitHub issue or directly.


## Technical information

- Each plugin should be an immutable singleton object.
- `ServiceLoader` is used for `.jar` plugins.
- Groovy plugin scripts are loaded and executed once and must return an object of one of the Plugin types.
- By convention, plugins live in `$BLACKLAB_CONFIG_DIR/plugins`:
    - `MyPlugin.jar` or `MyPlugin.groovy` contain the plugin code
    - `MyPlugin.yaml` (if exists) will be read and passed to the plugin as a `Map<String, Object>`.
    - `MyPlugin/` (if exists) will be passed to the plugin so it can read any files it needs from there.
- During development, you may want to delay initialization of plugins until they are used (normally, they are initialized on startup). This can be set in `blacklab[-server].yaml` under `plugins.delayInitialization` (default: `false`).
