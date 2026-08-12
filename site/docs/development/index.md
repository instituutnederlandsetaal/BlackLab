---
order: -1
---

# Development resources

## Getting BlackLab

### Getting BlackLab from Maven Central

BlackLab is in the Maven Central Repository, so you should be able to simply add it to your build tool, e.g.:

```xml
<dependency>
    <groupId>nl.inl.blacklab</groupId>
    <artifactId>blacklab</artifactId>
    <version>4.1.1</version>
</dependency>
```

If you're not sure what version to use, see the [downloads](downloads.html) or [changelog](changelog.html) pages.

### Downloading a prebuilt binary

BlackLab Core consists of a JAR and a set of required libraries. See the [GitHub releases page](https://github.com/instituutnederlandsetaal/BlackLab/releases/) and choose a jar-with-libs download. The latter one may also contain development versions you can try out.

BlackLab Server only consists of a WAR file that includes everything. You could even unzip this WAR file to obtain the included BlackLab JAR and zip files if you needed to for some reason.

### Building from source

If you want the very latest version (the "dev" branch) of BlackLab, you can easily build it from source code.

Either use Git to clone https://github.com/instituutnederlandsetaal/BlackLab or download a .zip file from GitHub.

Install JDK 17+ and build BlackLab using Maven:

```bash
mvn install
```

## A simple Java BlackLab application

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
	// Open your corpus
	try (BlackLabIndex index = BlackLab.open(new File("/home/zwets/testindex"))) {
	    String query = " \"the\" [pos=\"adj.*\"] \"brown\" \"fox\" ";
	
	    // Parse your query to get a TextPattern
	    TextPattern pattern = CorpusQueryLanguageParser.parse(query);
	
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

## More development documentation

The more development-related documentation is available in [the GitHub repository](https://github.com/instituutnederlandsetaal/BlackLab/tree/dev/doc/#readme). It includes various information about BlackLab's internals, such as the structure of the code, and details about file formats.
