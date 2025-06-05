# List input formats

Get the list of available input formats.

**URL** : `/blacklab-server/input-formats`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

### Content examples

A server with just the built-in formats might show the result below.

```json
{
  "user": {
    "loggedIn": false,
    "canCreateIndex": false
  },
  "supportedInputFormats": {
    "tsv": {
      "displayName": "TSV (tab-separated values)",
      "description": "A simple tabular format used by e.g. MS Excel. Column names (word, lemma, pos) are assumed, tab is separator, double quote is (optional) quote character, backslash the escape character.",
      "helpUrl": "https:\/\/github.com\/INL\/BlackLab\/blob\/dev\/core\/src\/site\/markdown\/tsv-example.md",
      "configurationBased": true,
      "isVisible": true
    },
    "tcf": {
      "displayName": "TCF (Text Corpus Format)",
      "description": "A text corpus format developed for WebLicht.",
      "helpUrl": "https:\/\/weblicht.sfs.uni-tuebingen.de\/weblichtwiki\/index.php\/The_TCF_Format",
      "configurationBased": true,
      "isVisible": true
    },
    "chat": {
      "displayName": "CHAT (Codes for the Human Analysis of Transcripts)",
      "description": "Format for transcribed conversations, created for the CHILDES project.",
      "helpUrl": "https:\/\/talkbank.org\/manuals\/CHAT.html",
      "configurationBased": true,
      "isVisible": true
    },
    "sketch-wpl": {
      "displayName": "Sketch Engine WPL (word-per-line) input format",
      "description": "A format including word, lemma and PoS codes as well as punctuation, inline tags and document metadata.",
      "helpUrl": "https:\/\/www.sketchengine.eu\/documentation\/preparing-corpus-text\/",
      "configurationBased": true,
      "isVisible": true
    },
    "txt": {
      "displayName": "plain text",
      "description": "A plain old text file.",
      "helpUrl": "https:\/\/en.wikipedia.org\/wiki\/Plain_text",
      "configurationBased": true,
      "isVisible": true
    },
    "tei-p5": {
      "displayName": "TEI P5, contents in text, @pos as PoS",
      "description": "A TEI P5 variant. The text element will be indexed as annotated contents. PoS tags should be in the pos attribute.",
      "helpUrl": "http:\/\/www.tei-c.org\/Guidelines\/P5\/",
      "configurationBased": true,
      "isVisible": true
    },
    "eaf": {
      "displayName": "EAF (Elan Annotation Format)",
      "description": "EAF format with Words, Lemma and PoS TIERs.",
      "helpUrl": "http:\/\/www.mpi.nl\/corpus\/html\/elan\/index.html",
      "configurationBased": true,
      "isVisible": true
    },
    "folia": {
      "displayName": "FoLiA (Format for Linguistic Annotation)",
      "description": "A file format for linguistically annotated text. See https:\/\/proycon.github.io\/folia\/",
      "helpUrl": "https:\/\/proycon.github.io\/folia\/",
      "configurationBased": true,
      "isVisible": true
    },
    "csv": {
      "displayName": "CSV (comma-separated values)",
      "description": "A simple tabular format used by e.g. MS Excel. Column names (word, lemma, pos) are assumed, comma is separator, double quote is (optional) quote character, backslash the escape character.",
      "helpUrl": "https:\/\/en.wikipedia.org\/wiki\/Comma-separated_values",
      "configurationBased": true,
      "isVisible": true
    },
    "tsv-frog": {
      "displayName": "Frog tagger TSV (tab-separated values)",
      "description": "Tab-delimited (TSV) output produced by the Frog program",
      "helpUrl": "http:\/\/languagemachines.github.io\/frog\/",
      "configurationBased": true,
      "isVisible": true
    },
    "tei-p5-legacy": {
      "displayName": "TEI P5 (legacy), contents in text, @type as PoS",
      "description": "A TEI P5 variant where part of speech is expected to be in the type attribute. Deprecated, will be removed eventually.",
      "helpUrl": "http:\/\/www.tei-c.org\/Guidelines\/P5\/",
      "configurationBased": true,
      "isVisible": true
    },
    "tei-p4-legacy": {
      "displayName": "TEI P4 (legacy), contents in text, @type as PoS",
      "description": "A TEI P4 variant where part of speech is expected to be in the type attribute. Deprecated, will be removed eventually.",
      "helpUrl": "http:\/\/www.tei-c.org\/Vault\/P4\/",
      "configurationBased": true,
      "isVisible": true
    },
    "cmdi": {
      "displayName": "CMDI (Component MetaData Infrastructure)",
      "description": "Metadata format initiated in the CLARIN project.",
      "helpUrl": "https:\/\/www.clarin.eu\/content\/component-metadata",
      "configurationBased": true,
      "isVisible": false
    }
  }
}
```

Notes:

- `configurationBased` indicates whether the format is based on a configuration file (modern) or a custom DocIndexer class (legacy).
- `isVisible` indicates whether the format should be shown in a GUI list of available formats. A format can be hidden by including `visible: false` in the format file.
