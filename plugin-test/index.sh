#!/bin/sh

java -cp '*:blacklab-convert-and-tag-indexer-5.0.0-SNAPSHOT.jar:lib' nl.inl.blacklab.tools.IndexTool create index input voice-tei
