# Using from different languages

Below are examples of using the REST API to fetch hits for a simple BlackLab Corpus Query Language query from different programming languages.

## JavaScript / TypeScript

Perform a CQL query and show matches in KWIC (keyword in context) format:

```javascript
// The BlackLab Server url for searching "mycorpus" (not a real URL)
var BASE_URL = "http://example.com/blacklab/mycorpus/";

// Show an array of hits in a table
function showHits(hits, docs) {
	
	// Context of the hit is passed in arrays, per annotation
	// (word/lemma/PoS). Right now we only want to display the 
	// words. This is how we join the word array to a string.
	function words(context) {
		return context['word'].join(" ");
	}
	
	// Iterate over the hits.
	// We'll add elements to the html array and join it later to produce our
	// final HTML.
	var html = ["<table><tr><th>Title</th><th>Keyword in context</th></tr>"];
	$.each(hits, function (index, hit) {
		
		// Add the document title and the hit information
		var doc = docs[hit['docPid']];
		html.push("<tr><td>" + doc['title'][0] + "</td><td>" + words(hit['left']) +
			" <b>" + words(hit['match']) + "</b> " + words(hit['right']) + 
			"</td></tr>");
	});
	html.push("</table>");
	output(html.join("\n")); // Join lines and append to output area
}

// Main program: performs a search and shows the results
function performSearch(patt) {
	
	// Clear the output area
	clear();
	
	// Carry out the request and call the showHits function
	$.ajax({
		url: BASE_URL + "hits",
		dataType: "json",
		data: {
			patt: patt
		},
		success: function (response) {
			// Got results. Show the hits, along with the document titles.
			showHits(response['hits'], response['docInfos']);
		}
	});
}

// Clear output area
function clear() {
	$('#output').html('');
}

// Add HTML to the output area
function output(addHtml) {
	$('#output').append(addHtml).append("\n");
}
```

## Python 3

Perform a CQL query and show matches in KWIC (keyword in context) format:

```python
import urllib.parse
import urllib.request
import json

def words(context):
	""" Convert word array to string. """
	return " ".join(context['word'])

def search(cqlQuery):
	""" Search and show hits. """
	url = "http://example.com/blacklab/mycorpus/hits?patt=" + \
		urllib.parse.quote_plus(cqlQuery) + "&outputformat=json"
	f = urllib.request.urlopen(url)
	response = json.loads(f.read().decode('utf-8'))
	hits = response['hits']
	docs = response['docInfos']
	for hit in hits:
		# Show the document title and hit information
		doc = docs[hit['docPid']]
		print(words(hit['left']) + " [" + words(hit['match']) + "] " + \
			words(hit['right']) + " (" + doc['title'][0] + ")")

# "Main program"
search('[pos="a.*"] "fox"')
```

## Python 2.7

Perform a CQL query and show matches in KWIC (keyword in context) format:

```python
import urllib
import json

def words(context):
	""" Convert word array to string. """
	return " ".join(context['word'])

def search(cqlQuery):
	""" Search and show hits. """
	url = "http://example.com/blacklab/mycorpus/hits?patt=" + \
		urllib.quote_plus(cqlQuery) + "&outputformat=json"
	f = urllib.urlopen(url)
	response = json.loads(f.read())
	hits = response['hits']
	docs = response['docInfos']
	for hit in hits:
		# Show the document title and hit information
		doc = docs[hit['docPid']]
		print(words(hit['left']) + " [" + words(hit['match']) + "] " + \
				words(hit['right']) + " (" + doc['title'][0] + ")")

# "Main program"
search('[pos="a.*"] "fox"')
```

## Java

Perform a CQL query and show matches in KWIC (keyword in context) format:

```java
import org.json.simple.*;
import java.io.*;
import java.net.*;

class BlackLabServerTest {

	/** The BlackLab Server url for searching "mycorpus" (not a real URL) */
	final static String BASE_URL = "http://example.com/blacklab/mycorpus/";
	
	/** Fetch the specified URL and decode the returned JSON.
		* @param url the url to fetch
		* @return the page fetched
		*/
	public static JsonNode fetch(String url) throws Exception {
		// Read from the specified URL.
		InputStream is = new URL(url).openStream();
		try {
			String line;
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			StringBuilder b = new StringBuilder();
			while ((line = br.readLine()) != null) {
				b.append(line);
			}
			return new JsonNode(b.toString());
		} finally {
			is.close();
		}
	}
	
	/** Context of the hit is passed in arrays, per annotation
		* (word/lemma/PoS). Right now we only want to display the 
		* words. This is how we join the word array to a string.
		* @param context context structure containing word, lemma, PoS.
		* @return the words joined together with spaces.
		*/
	static String words(JsonNode context) {
		JsonNode words = (JsonNode)context.get("word");
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < words.size(); i++) {
			if (b.length() > 0)
				b.append(" ");
			b.append((String)words.get(i));
		}
		return b.toString();
	}
	
	/** Show an array of hits in an HTML table.
		* @param hits the hits structure from the JSON response
		* @param docs the docInfos structure from the JSON response
		*/
	public static void showHits(JsonNode hits, JsonNode docs) {
		
		// Iterate over the hits.
		// We'll add elements to the html array and join it later to produce our
		// final HTML.
		StringBuilder html = new StringBuilder();
		html.append("<table><tr><th>Title</th><th>Keyword in context</th></tr>\n");
		for (int i = 0; i < hits.length(); i++) {
			JsonNode hit = (JsonNode)hits.get(i);
			
			// Add the document title and the hit information
			JsonNode doc = (JsonNode)docs.get((String)hit.get("docPid"));
			
			// Context of the hit is passed in arrays, per annotation
			// (word/lemma/PoS). Right now we only want to display the 
			// words. This is how we join the word array to a string.
			String left = words((JsonNode)hit.get("left"));
			String match = words((JsonNode)hit.get("match"));
			String right = words((JsonNode)hit.get("right"));
			
			html.append("<tr><td>" + (String)doc.get("title").get(0) + "</td><td>" + left +
				" <b>" + match + "</b> " + right + "</td></tr>\n");
		}
		html.append("</table>\n");
		System.out.println(html.toString()); // Join lines and output
	}
	
	
	/** Performs a search and shows the results.
		* @param patt the pattern to search for
		*/
	public static void performSearch(String patt) throws Exception {
		
		// Carry out the request and call the showHits function
		String url = BASE_URL + "hits?patt=" + URLEncoder.encode(patt, "utf-8") + "&outputformat=json";
		JsonNode response = fetch(url);
		
		// Got results. Show the hits, along with the document titles.
		JsonNode hits = (JsonNode)response.get("hits");
		JsonNode docs = (JsonNode)response.get("docInfos");
		showHits(hits, docs);
	}
	
	/** Main method.
		* @param argv command-line arguments
		*/
	public static void main(String[] argv) throws Exception {
		performSearch("[pos=\"a.*\"] \"fox\"");
	}
	
}
```

## R

Perform a CQL query and show matches in KWIC (keyword in context) format:

```r
suppressMessages(library("RCurl"))
suppressMessages(library("rjson"))

# Convert word array to string.
words <- function(context) {
	return(paste(context[['word']], collapse=" "))
}

# Search and show hits.
search <- function(cqlQuery) {
	url <- paste("http://example.com/blacklab/mycorpus/hits?patt=", 
			curlEscape(cqlQuery), "&outputformat=json", sep="")
	lines <- suppressWarnings(readLines(url))  # suppress "Incomplete final line"
	response <- fromJSON(paste(lines, collapse=""))
	docs <- response[['docInfos']]
	hits <- response[['hits']]
	for(hit in hits) {
		# Add the document title and the hit information
		doc <- docs[[ hit[['docPid']] ]];
		cat(paste(words(hit[['left']]),
			" [", words(hit[['match']]), "] ", words(hit[['right']]),
			" (", doc[['title']][[0]], ")\n", sep="", collapse="\n"))
	}
	return()
}

invisible(search('[pos="a.*"] "fox"'))
```

## Ruby

Perform a CQL query and show matches in KWIC (keyword in context) format:

```ruby
require 'json'
require 'open-uri'

# The BlackLab Server url for searching "mycorpus" (not a real URL)
BASE_URL = "http://corpus.ivdnt.org/blacklab/mycorpus/"

# Simulate fetching URL?
DEBUG = true

def fetch(url)
	""" Fetch the specified URL and decode the returned JSON. """
	if DEBUG
		print "Simulate fetch from #{url}"
		url = 'testdata/test.json'
	end
	return JSON.parse(open(url).read)
end

def showHits(hits, docs)
	""" Show an array of hits in a table """
	
	# Context of the hit is passed in arrays, per annotation
	# (word/lemma/PoS). Right now we only want to display the 
	# words. This is how we join the word array to a string.
	def words(context)
		return context['word'].join(" ")
	end
	
	print "<table><tr><th>Title</th><th>Keyword in context</th></tr>\n"
	hits.each do |hit|
		# Show the document title and hit information
		doc = docs[hit['docPid']]
		print "<tr><td>#{doc['title'][0]}</td>" +
			"<td>#{words(hit['left'])} <b>#{words(hit['match'])}</b> " +
			"#{words(hit['right'])}</td></tr>\n"
	end
	print "</table>\n"
end

def performSearch(patt)
	""" Main program: performs a search and shows the results """
	
	# Carry out the request and parse the response JSON
	response = fetch("#{BASE_URL}hits?patt=#{URI::encode(patt)}&outputformat=json")
	
	# Show the hits, along with the document titles
	showHits(response['hits'], response['docInfos'])
end

performSearch('[pos="a.*"] "fox"')
```

## PHP

Perform a CQL query and show matches in KWIC (keyword in context) format:

```php
<?php

// The BlackLab Server url for searching "mycorpus" (not a real URL)
define("BASE_URL", "http://example.com/blacklab/mycorpus/");

// Fetch the specified URL and decode the returned JSON.
function fetch($url) {
	// Read from the specified URL.
	$responseTxt = file_get_contents($url);
	return json_decode($responseTxt, true);
}

// Show an array of hits in a table
function showHits($hits, $docs) {
	print "<table><tr><th>Title</th><th>Keyword in context</th></tr>\n";

	// Context of the hit is passed in arrays, per annotation
	// (word/lemma/PoS). Right now we only want to display the 
	// words. This is how we join the word array to a string.
	function words($context) {
		return join(" ", $context['word']);
	}
	
	foreach($hits as $hit) {
		
		// Get the document metadata so we can print the title.
		$doc = $docs[$hit['docPid']];
		
		// Show the hit information
		$tableRow = "<tr><td>%s</td><td>%s <b>%s</b> %s</td></tr>\n";
		print sprintf($tableRow, $doc['title'][0], words($hit['left']), 
			words($hit['match']), words($hit['right']));
	}
	print "</table>";
}
	
// Main program: performs a search and shows the results
function performSearch($patt) {
	
	// Carry out the request and parse the response JSON
	$response = fetch(BASE_URL."hits?patt=".urlencode($patt))."&outputformat=json";
	
	// Show the hits, along with the document titles
	showHits($response['hits'], $response['docInfos']);
}

// Run the main program
performSearch('[pos="a.*"] "fox"');

?>
```

## Perl

Perform a CQL query and show matches in KWIC (keyword in context) format:

```perl
use strict;
use warnings;
use WebService::Simple;
use JSON::Parse ':all';

# The BlackLab Server url for searching "mycorpus" (not a real URL)
my $BASE_URL = "http://example.com/blacklab/mycorpus/";

# Perform the search and decode the returned JSON.
sub fetchSearchResults {
	my ($patt) = @_;
	
	my $response;
	# Initialize WebService::Simple for JSON webservice
	my $blacklab = WebService::Simple->new(
		base_url        => $BASE_URL,
		response_parser => 'JSON'
	);
	
	# Send query
	my $responseObj = $blacklab->get("hits", { patt => $patt, outputformat => "json" } );
	$response = $responseObj->parse_response;
}

# Show an array of hits in a table
sub showHits {
	my ($hits, $docs) = @_; # Unpack parameters
	
	print "<table><tr><th>Title</th><th>Keyword in context</th></tr>\n";
	foreach my $hit (@$hits) {
		# Get the document metadata so we can print the title.
		my $doc = $docs->{$hit->{'docPid'}};
		
		# Context of the hit is passed in arrays, per annotation
		# (word/lemma/PoS). Right now we only want to display the 
		# words. Join the arrays into strings.
		my $left  = join(" ", @{$hit->{'left'}{'word'}});
		my $match = join(" ", @{$hit->{'match'}{'word'}});
		my $right = join(" ", @{$hit->{'right'}{'word'}});
		
		# Show the hit information
		print "<tr><td>".$doc->{'title'}[0]."</td>".
			"<td>$left<b>$match</b>$right</td></tr>\n";
	}
	print "</table>\n";
}

# Main program: performs a search and shows the results
sub performSearch {
	my ($patt) = @_; # Unpack parameters
	my $response = &fetchSearchResults($patt);
	&showHits($response->{'hits'}, $response->{'docInfos'});
}

# Run the main program
&performSearch('"quick"');
```
