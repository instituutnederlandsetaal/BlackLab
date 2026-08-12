--- 
title: Getting started (no Docker)
---
# Start using BlackLab (without Docker)

We'll run through a simple example: index a corpus, run Blacklab Server and access the corpus using the API and the frontend.

**NOTE:** This is the non-Docker starting guide. The recommended way to use BlackLab is with Docker; see [here](./getting-started).

This guide assumes you're running a Debian-based Linux. It should be easily adaptable to i.e. Fedora or OSX, and you should be able to get it to work in WSL as well.


### A place for your corpora

Create an empty directory where you want to store your indexed corpora. Make sure it has the permissions you require. For example:

```bash
# Create directory
sudo mkdir -p /data/blacklab-corpora

# Make sure you own it, so you can create corpora there
sudo chown -R $USER:$GROUP /data/blacklab-corpora

# Make sure it's world-readable so e.g. Tomcat can read it
chmod -R a+rx /data/blacklab-corpora
```


### Index a corpus

You will need a JVM version of 17 or higher to use the latest BlackLab versions:

```bash
sudo apt install openjdk-17-jdk
```

There's a commandline tool to create a corpus called `IndexTool`. To use it, also download the blacklab-tools-_VERSION_.zip from the [GitHub releases page](https://github.com/instituutnederlandsetaal/BlackLab/releases/) and extract it somewhere convenient.

From this directory, run the IndexTool without parameters for help information:

```bash
java -cp "*" nl.inl.blacklab.tools.IndexTool
```

(`blacklab-tools-VERSION.jar` and the `lib` subdirectory containing required libraries should be located in the current directory)

We want to create a new corpus, so we need to supply a corpus (index) directory, input file(s) and an input format.

For this example, download or copy [alice.xml](@github:/contrib/test-data/alice.xml) and [tei-p5.blf.yaml](@github:/contrib/input-formats/tei-p5.blf.yaml) from the BlackLab repository. Then, from the same directory, run:

```bash
java -cp "*" nl.inl.blacklab.tools.IndexTool create /data/blacklab-corpora/test alice.xml tei-p5
```

This will create a new corpus and index `alice.xml` using the `tei-p5` format.

Instead of a single file like `alice.xml`, you can also specify a a `.zip` or `.tar.gz` file containing multiple documents or a directory.

If you look at the `alice.xml` input file, you can see that it is indeed in TEI P5 XML format, and that it is _tokenized_ and _annotated_, that is: each word is tagged separately and has `lemma` and `pos` annotations. BlackLab always needs tokenized input files. It is possible to automatically tokenize files before indexing using a plugin, but that's a more advanced topic.

BlackLab comes with a number of [example format configurations](@github:/contrib/input-formats/) including TEI, FoLiA and CoNLL-U. You can adapt these to your needs or create entirely new ones. To learn all about `.blf.yaml` files, see [Index your data](/guide/index-your-data/).

Now, let's test the index we just created.

### Check with QueryTool

(NOTE: this step is optional)

Just to test that indexing was succesful, you can use a developer utility included with BlackLab, QueryTool:

```bash
java -cp "*" nl.inl.blacklab.tools.QueryTool /data/blacklab-corpora/my-corpus
```

Some help information will be printed and a `BCQL>` prompt appears. To find the word Alice followed by a verb, type:

```
"Alice" [pos="VERB"]
```

You should see two hits.

Great, the corpus was succesfully indexed. Type `exit` to close the QueryTool.

See also:
- [QueryTool](/development/query-tool.html)
- [BlackLab Corpus Query Language (BCQL)](https://blacklab.ivdnt.org/guide/query-language/)


### Run the BlackLab Server API

First, create a directory `/etc/blacklab` with a file named `blacklab-server.yaml`:

```bash
# Create directory
sudo mkdir /etc/blacklab

# Create empty config file and take ownership of it
sudo touch /etc/blacklab/blacklab-server.yaml
sudo chown $USER:$GROUP /etc/blacklab/blacklab-server.yaml

# Make sure Tomcat can access the directory and file
sudo chmod -R a+rX /etc/blacklab
```

(NOTE: if you cannot create a directory under `/etc/`, see the TIP under [configuration file](/server/#configuration-file) for alternatives)

Now, edit the file `/etc/blacklab/blacklab-server.yaml` using a text editor:

```yaml
---
configVersion: 2

# Where corpora can be found
# (list directories whose subdirectories are corpora, or directories containing a single corpus)
indexLocations:
- /data/blacklab-corpora
```

For the REST API, you will need a version of Apache Tomcat installed. Use Tomcat 9 for BlackLab 4.x and Tomcat 10 for BlackLab dev/future 5.x:

```bash
sudo apt install tomcat9
```

**NOTE:** tomcat9 is quite old and may not be available in your package manager anymore. You can also go to https://tomcat.apache.org/download-90.cgi, download the binary .tar.gz distribution, extract it locally and start it with `bin/startup.sh`.

On the [GitHub releases page](https://github.com/instituutnederlandsetaal/BlackLab/releases/), find the version you want to run and download the attached file named `blacklab-server-VERSION.war`. Rename it to `blacklab-server.war` and place this file in Tomcat's `webapps` directory. Tomcat should automatically recognize the file and initialize the application (usually, it will extract it to a subdirectory). If it doesn't, restart tomcat to be sure.

Go to http://localhost:8080/blacklab-server/. You will see the server info page. In the browser, you will get the XML response by default. There is also a [JSON response](http://localhost:8080/blacklab-server/?outputformat=json), which is the preferred way to communicate with the API. The server info page should include the available `corpora`, with the one named `test` you just indexed.

::: details <b>TIP:</b> Unicode URLs
To ensure the correct handling of accented characters in (search) URLs, you should [configure Tomcat](https://tomcat.apache.org/tomcat-9.0-doc/config/http.html#Common_Attributes) to interpret URLs as UTF-8 (by default, it does ISO-8859-1) by adding an attribute `URIEncoding="UTF-8"` to the `<Connector/>` element with the attribute `port="8080"` in Tomcat's `server.xml` file.

Of course, make sure that URLs you send to BlackLab are URL-encoded using UTF-8 (so e.g. searching for `"señor"` corresponds to a request like `http://myserver/blacklab-server/mycorpus/hits?patt=%22se%C3%B1or%22`). [BlackLab Frontend](https://blacklab-frontend.ivdnt.org/) does this by default.
:::

To search your corpus using the API, try:

    # search for "Alice" [pos="VERB"] again 
    http://localhost:8080/blacklab-server/corpora/test/hits?
        api=5&outputformat=json&patt=%22Alice%22%20%5Bpos%3D%22VERB%22%5D

This is the same query you just performed in the QueryTool. Dig into it if you're curious, but the most important thing is that the API is working. Now we can move on to getting BlackLab Frontend running as well.

Learn all about using the API at https://blacklab.ivdnt.org/server/rest-api/


### Add BlackLab Frontend

Download a `.war` file (ideally with the same major version as the BlackLab version you're using) from the https://github.com/instituutnederlandsetaal/blacklab-frontend/releases page, i.e. `blacklab-frontend-VERSION.war`, rename it to `blacklab-frontend.war` and place it in Tomcat's `webapps` directory alongside the BlackLab Server `.war`. Tomcat should deploy it automatically.

After a few seconds, you should be able to go to http://localhost:8080/blacklab-frontend/test/search/ to see the BlackLab Frontend search interface.

Using and customizing the frontend is explained at [blacklab-frontend.ivdnt.org](https://blacklab-frontend.ivdnt.org/)

## Search from Python

Below is an example of accessing your corpus from Python. It performs a BCQL query and shows matches in KWIC (keyword in context) format:

```python
import urllib.parse
import urllib.request
import json

def words(context):
	""" Convert word array to string. """
	return " ".join(context['word'])

def search(cqlQuery):
	""" Search and show hits. """
	url = "http://localhost:8080/blacklab-server/corpora/test/hits?api=5&patt=" + \
		urllib.parse.quote_plus(cqlQuery) + "&outputformat=json"
	f = urllib.request.urlopen(url)
	response = json.loads(f.read().decode('utf-8'))
	hits = response['hits']
	docs = response['docInfos']
	for hit in hits:
		# Show the document title and hit information
		doc = docs[hit['docPid']]
		print(words(hit['before']) + " [" + words(hit['match']) + "] " + \
			words(hit['after']) + " (" + doc['title'][0] + ")")

# "Main program"
search('"Alice" [pos="VERB"]')
```

More examples can be found [here](/server/from-different-languages.md).
