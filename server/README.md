BlackLab Server
===============

A webservice that allows you to use corpus search engine BlackLab from any programming language.

**WARNING: This is a beta version, available for review and testing. There may still be bugs or performance issues. **

Please see the [full overview](https://blacklab.ivdnt.org/blacklab-server-overview.html) of the webservice for more information.

Here's some simple example URLs:

Find hits for a word:

  http://corpus.example.com/blacklab-server/mycorpus/hits?patt="easy"

Group hits by word before match:

  http://corpus.example.com/blacklab-server/mycorpus/hits?patt="easy"&group=before:word:s:1

Highlight in original content:

  http://corpus.example.com/blacklab-server/mycorpus/docs/12345/?patt="easy"

