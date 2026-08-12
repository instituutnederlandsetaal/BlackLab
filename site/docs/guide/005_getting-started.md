--- 
title: Getting started
---
# Start using BlackLab

We'll run through a simple example: index a corpus, run Blacklab Server and access the corpus using the API and the frontend.

**NOTE:** Using Docker is probably the easiest way to work with BlackLab. However, if you prefer to run it without Docker, see [here](getting-started) instead.

This assumes you're running Linux, but it should work (with minor changes) under OSX or Windows using WSL as well.

### Required files

The guide below assumes you have a copy of the BlackLab repository on your machine. Either use:

```bash
git clone https://github.com/instituutnederlandsetaal/BlackLab
```

or download and extract a zip file from GitHub.

If you prefer, you can download only the files needed from the repository:
- scripts [index-corpus.sh](@github:/scripts/index-corpus.sh) and [query-corpus.sh](@github:/scripts/query-corpus.sh)
- the test data [alice.xml](@github:/contrib/test-data/alice.xml)
- the input format configuration [tei-p5.blf.yaml](@github:/contrib/input-formats/tei-p5.blf.yaml)


### A place for your corpora

Create an empty directory where you want to store your indexed corpora. Make sure it has the right permissions.

For example:

```bash
# Create directory
sudo mkdir -p /data/blacklab-corpora

# Make sure you own it, so you can create corpora there
sudo chown -R $USER:$GROUP /data/blacklab-corpora

# Make sure it's world-readable so e.g. Tomcat can read it
chmod -R a+rx /data/blacklab-corpora
```

### Index a corpus

The `scripts` directory in the BlackLab repository contains `index-corpus.sh`, which uses Docker to create a corpus from input data files. Ensure Docker is installed and is a recent version (at least v23).

A note about versions: in this example, we will use the `dev` version of BlackLab, but if you want to use a numbered version with these two Bash scripts, set de `BL_VERSION` environment variable:

```bash
# Configure the scripts to use a numbered release rather than dev
export BL_VERSION=4.1.1
```

To index a corpus, run:

```bash
scripts/index-corpus.sh /data/blacklab-corpora/test contrib/test-data/alice.xml contrib/input-formats/tei-p5.blf.yaml
```

This will take the file `alice.xml` and index it using the `tei-p5.blf.yaml` input format configuration. It will save the indexed corpus at `/data/blacklab-corpora/test` (or whatever other location you choose, of course).

If you look at the `alice.xml` input file, you can see that it is indeed in TEI P5 XML format, and that it is _tokenized_ and _annotated_, that is: each word is tagged separately and has `lemma` and `pos` annotations. BlackLab always needs tokenized input files. It is possible to automatically tokenize files before indexing using a plugin, but that's a more advanced topic.

BlackLab comes with a number of [example format configurations](@github:/contrib/input-formats/) including TEI, FoLiA and CoNLL-U. You can adapt these to your needs or create entirely new ones. To learn all about `.blf.yaml` files, see [Index your data](/guide/index-your-data/).

Now, let's test the index we just created.

### Check with QueryTool

(NOTE: this step is optional)

Just to test that indexing was successful, you can use a developer utility included with BlackLab, QueryTool. The script `query-corpus.sh` will run this tool. Run:

```bash
scripts/query-corpus.sh /data/blacklab-corpora/test
```

Some help information will be printed and a `BCQL>` prompt appears. To find the word Alice followed by a verb, type:

```
"Alice" [pos="VERB"]
```

You should see two hits.

Great, the corpus was successfully indexed. Type `exit` to close the QueryTool.

See also:
- [QueryTool](/development/query-tool.html)
- [BlackLab Corpus Query Language (BCQL)](https://blacklab.ivdnt.org/guide/query-language/)


### Run the BlackLab Server API

To access BlackLab's API from, say, a Python script, or for setting up BlackLab Frontend, create a file `docker-compose.yml`:

```yaml
---
services:

  blacklab:
    image: instituutnederlandsetaal/blacklab:dev
    environment:
      # Give JVM enough heap memory
      - "JAVA_OPTS=-Xmx2G"
    ports:
      # Expose port 8080 on the host so we can access the API
      - "8080:8080"
    volumes:
      # BlackLab will look for corpora here 
      - /data/blacklab-corpora:/data/index
```

Save the file and run:

```bash
docker compose pull
docker compose up
```

You will see log messages as BlackLab Server is starting up. Once you see the "Server startup in ... milliseconds" message, BlackLab is ready.

Go to http://localhost:8080/blacklab-server/. You will see the server info page. In the browser, you will get the XML response by default. There is also a [JSON response](http://localhost:8080/blacklab-server/?outputformat=json), which is the preferred way to communicate with the API. The server info page should include the available `corpora`, with the one named `test` you just indexed.

To search your corpus using the API, try:

    # search for "Alice" [pos="VERB"] again 
    http://localhost:8080/blacklab-server/corpora/test/hits?
        api=5&outputformat=json&patt=%22Alice%22%20%5Bpos%3D%22VERB%22%5D

This is the same query you just performed in the QueryTool. Dig into the response if you're curious, but the most important thing is that the API is working. Now we can move on to getting BlackLab Frontend running as well.

To learn all about BlackLab Server, there's an [API reference](/server/rest-api/).

To stop the server again, bring up the terminal where you started it and press `Ctrl+C`. The server should shut down gracefully.

### Add BlackLab Frontend

To run a container with both BlackLab and Frontend together, change the `image:` line in the `docker-compose.yml` file as follows:

```yaml
    image: instituutnederlandsetaal/blacklab-frontend:dev
```

`blacklab-frontend` is extends the `blacklab` image to include BlackLab Frontend as well.

To pull the image and start the server, running in the background this time:

```bash
docker compose pull
docker compose up -d
```

After a few seconds, you should be able to go to http://localhost:8080/blacklab-frontend/test/search/ to see the BlackLab Frontend search interface.

Using and customizing the frontend is explained at [blacklab-frontend.ivdnt.org](https://blacklab-frontend.ivdnt.org/)

To stop the server again, use `docker compose stop`.

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
