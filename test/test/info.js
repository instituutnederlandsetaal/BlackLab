"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const { expectUnchanged, expectUrlUnchanged, expectCorpusUrlUnchanged} = require("./compare-responses");
const constants = require('./constants');
const SERVER_URL = constants.SERVER_URL;

// Server info
expectUrlUnchanged('test', 'info', 'server', '/'); // ?api=exp&custom=true
expectUrlUnchanged('test', 'info', 'input formats', '/input-formats');

// Corpus info
expectCorpusUrlUnchanged('test', 'info', 'corpus', '/');
expectCorpusUrlUnchanged('test', 'info', 'corpus status', '/status');

// Relations
expectCorpusUrlUnchanged('test', 'info', 'relations', '/relations');

// Field info with list of values
expectCorpusUrlUnchanged('test', 'info', 'annotated field info with values',
        '/fields/contents?listvalues=lemma');
expectCorpusUrlUnchanged('test', 'info', 'metadata field info with values',
        '/fields/title');

// Autocomplete
expectCorpusUrlUnchanged('test', 'info', 'autocomplete metadata field',
        '/autocomplete/title?term=a');
expectCorpusUrlUnchanged('test', 'info', 'autocomplete annotated field',
        '/autocomplete/contents/lemma?term=b');
