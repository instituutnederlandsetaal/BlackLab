---
#home: true
#heroImage: /hero.png
heroText: BlackLab Hero
tagline: Hero subtitle
actionText: Get Started →
actionLink: /blacklab/
features:
- title: Simplicity First
  details: Minimal setup with markdown-centered project structure helps you focus on writing.
- title: Vue-Powered
  details: Enjoy the dev experience of Vue + webpack, use Vue components in markdown, and develop custom themes with Vue.
- title: Performant
  details: VuePress generates pre-rendered static HTML for each page, and runs as an SPA once a page is loaded.
footer: MIT Licensed | Copyright © 2018-present Evan You
---

# BlackLab documentation


## New Indexing structure

- how to index
  - indextool
    - faster indexing
  - index using BLS

- document formats
  - built-in formats
  - a simple custom format
  - xpath support level
  - processing values
  - allow viewing documents
  - automatic xslt generation
  - additional index metadata
  - reducing index size
  - extend existing formats
  - annotated configuration file

- non-xml input files
  - indexing CSV/TSV files
  - indexing plain text files
  - indexing Word, PDF, etc.

- annotations
  - case- and diacritics sensitivity
  - multiple values at one position
  - indexing raw xml
  - standoff annotations
  - subannotations

- metadata
  - embedded metadata
  - external metadata
  - fixed metadata value
  - tokenized vs untokenized
  - default gui widgets



## Overall structure

This is the intended structure of the new BlackLab documentation:

- Try

  - What is it?
  - Who is it for?
  - Features
  - Quick Start for evaluating BlackLab Server and Frontend
    - Docker
    - Tomcat
  - Test Index
  - Search your corpus
  - Advanced: BlackLab Corpus Query Language

- BlackLab Server, a webservice
  
  - What is it?
  - Who is it for?
  - Basic example
  - Getting started
    - Basic installation, configuration
      - Docker
      - Tomcat
    - Indexing data
      - IndexTool
      - via the webservice
    - Manual use in the browser
    - Using BLS from different languages
  - REST API reference
  - Detailed configuration
  - Tutorials / howtos
    - User authentication, creating indices and adding data
    - Convert/Tag plugins

- BlackLab Frontend, a web application
 
  - What is it?
  - Who is it for?
  - Getting started
    - Basic installation, configuration
      - Docker
      - Tomcat
    - Using the application
  - UI reference
  - Detailed configuration
  - Tutorials / howtos
    - Customizing functionality

- BlackLab Corpus Query Language

- Developers
 
  - BlackLab Core, the Java library

    - Basic example

  - Tutorials / howtos
  
    - A custom analysis script
    - Using the forward index
    - Using capture groups
    - Indexing a different input format

  - Customization
  
    - Implementing a custom query language
    - Implementing a custom DocIndexer
    - Implementing a Convert/Tag plugin

  - API Reference

  - Internals
    - File formats

