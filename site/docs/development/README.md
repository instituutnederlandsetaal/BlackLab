# Development resources

## BlackLab Core

The Java library


First you need to get the BlackLab library. The simplest way is to let Maven download it automatically from the Central Repository, but you can also download a prebuilt binary, and it's trivial to build it yourself.

<blockquote>
<b>Note to MacOS users</b>: Dirk Roorda at DANS wrote a detailed guide for installing and indexing data on MacOS. It's available <a href='https://github.com/Dans-labs/clariah-gm/blob/master/blacklab/install.md'>here</a>. It's also archived <a href="../server/install-macos.html">here</a>.
</blockquote>

## Getting BlackLab

### Getting BlackLab from Maven Central

BlackLab is in the Maven Central Repository, so you should be able to simply add it to your build tool, e.g.:

```xml
<dependency>
    <groupId>nl.inl.blacklab</groupId>
    <artifactId>blacklab</artifactId>
    <version>3.0.1</version>
</dependency>
```

If you're not sure what version to use, see the [downloads](downloads.html) or [changelog](changelog.html) pages.

### Downloading a prebuilt binary

BlackLab Core consists of a JAR and a set of required libraries. See the [GitHub releases page](https://github.com/instituutnederlandsetaal/BlackLab/releases/) and choose a jar-with-libs download. The latter one may also contain development versions you can try out.

BlackLab Server only consists of a WAR file that includes everything. You could even unzip this WAR file to obtain the included BlackLab JAR and zip files if you needed to for some reason.

### Building from source

If you want the very latest version (the "dev" branch) of BlackLab, you can easily build it from source code.

First, you need to download the source code from GitHub. You can download it from there in a .zip file (be sure to select the dev branch before doing so), but a better way to get it is by cloning it using Git. [Install a Git client](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) (we'll give command line examples here, but it should translate easily to GUI clients like TortoiseGit), change to a directory where you keep your projects, and clone BlackLab:

	git clone git://github.com/instituutnederlandsetaal/BlackLab.git

Git will download the project and place it in a subdirectory "BlackLab". Now switch to the dev branch:

    git checkout dev
    
Install a recent JDK (Java Development Kit). If you're on Linux, you can use your package manager to do this (OpenJDK is fine too). Note that you will need at least JDK version 8 (i.e. openjdk-1.8.0) to use the latest BlackLab versions.

BlackLab is built using [Maven](http://maven.apache.org/), a popular Java build tool. [Install Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) (use your package manager if on Linux), change into the BlackLab directory, and build the library:

	mvn install

("install" refers to the fact that the library is "installed" to your private Maven repository after it is built)

After a lot of text output, it should say "BUILD SUCCESS" and the BlackLab JAR library should be under core/target/blacklab-VERSION.jar (where VERSION is the current BlackLab version, i.e. "1.7-SNAPSHOT"; SNAPSHOT means it's not an official release, by the way). The BlackLab Server WAR will be in server/target/blacklab-server-VERSION.war.

::: tip NOTE
If you want to use BlackLab Server and [BlackLab Frontend](/frontend/) (our search application), you'll need an application server like Apache Tomcat too. Also available via package manager in Linux. After installation, find the `webapps` directory (e.g. `/var/lib/tomcat/webapps/`, but may depend on distribution) and copy the WAR file to it. It should be extracted by Tomcat automatically. For full installation and configuration instructions, see [BlackLab Server overview](/server/overview.md).
:::

## A simple BlackLab application

Finally, let's look at an example Java application.

Here’s the basic structure of a BlackLab search application, to give you an idea of where to look in the source code and documentation (note that we leave nl.inl.blacklab out of the package names for brevity):

1. Call BlackLab.open() to instantiate a BlackLabIndex object. This provides the main BlackLab API.
2. Construct a TextPattern structure that represents your query. You may want to do this from a query parser, or use one of the query parsers supplied with BlackLab (CorpusQueryLanguageParser, …).
3. Call the BlackLabIndex.find() method to execute the TextPattern and return a Hits object. (Internally, this translates the TextPattern into a Lucene SpanQuery, executes it, and collects the hits. Each of these steps may also be done manually if you wish to have more control over the process)
4. Sort or group the results, using Hits.sort() or Hits.group() and a HitProperty object to indicate the sorting/grouping criteria.
5. Select a few of your Hits to display by calling Hits.window().
6. Loop over the HitsWindow and display each hit.
7. Close the BlackLabIndex object.

The above in code:

```java
	// Open your index
	try (BlackLabIndex index = BlackLab.open(new File("/home/zwets/testindex"))) {
	    String corpusQlQuery = " \"the\" [pos=\"adj.*\"] \"brown\" \"fox\" ";
	
	    // Parse your query to get a TextPattern
	    TextPattern pattern = CorpusQueryLanguageParser.parse(corpusQlQuery);
	
	    // Execute the TextPattern
	    Hits hits = index.find(pattern);
	
	    // Sort the hits by the words to the left of the matched text
	    HitProperty sortProperty = new HitPropertyBeforeHit(index, index.annotation("word"));
	    hits = hits.sort(sortProperty);
	
	    // Limit the results to the ones we want to show now (i.e. the first page)
	    Hits window = hits.window(0, 20);
	
	    // Iterate over window and display the hits
	    Concordances concs = hits.concordances(ContextSize.get(5));
	    for (Hit hit: window) {
	        Concordance conc = concs.get(hit);
	        // Strip out XML tags for display.
	        String left = XmlUtil.xmlToPlainText(conc.left);
	        String hitText = XmlUtil.xmlToPlainText(conc.hit);
	        String right = XmlUtil.xmlToPlainText(conc.right);
	        System.out.printf("%45s[%s]%s\n", left, hitText, right);
	    }
	
	}
```

See also:

<!-- - [API reference](apidocs/index.html) -->
- [The included example application](examples/example-application.md)



## Tutorials / howtos

### A custom analysis script


### Using the forward index


### Using capture groups


### Indexing a different input format



## Internals

The more in-depth information about BlackLab's internals, such as the structure of the code, and details about file formats, is available in [the GitHub repository](https://github.com/instituutnederlandsetaal/BlackLab/tree/dev/doc/#readme), along with other documentation related to development.
