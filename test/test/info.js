"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const { expectUnchanged, expectUrlUnchanged} = require("./compare-responses");
const constants = require('./constants');
const SERVER_URL = constants.SERVER_URL;

describe('info/Server info page', () => {
    it('should return server info', done => {
        chai
            .request(constants.SERVER_URL)
            .get('/')
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
            .end((err, res) => {
                if (err)
                    done(err);
                
                expect(res, 'response').to.have.status(200);
                expectUnchanged('test', 'info', 'Server info page', res.body);
                done();
            });
    });
});

// Server info
expectUrlUnchanged('test', 'info', 'server', '/'); // ?api=exp&custom=true
expectUrlUnchanged('test', 'info', 'input formats', '/input-formats');

// Corpus info
expectUrlUnchanged('test', 'info', 'corpus', constants.URL_CORPUS_TEST + '/');
expectUrlUnchanged('test', 'info', 'corpus status', constants.URL_CORPUS_TEST + '/status');

// Relations
expectUrlUnchanged('test', 'info', 'relations', constants.URL_CORPUS_TEST + '/relations');

// Field info with list of values
expectUrlUnchanged('test', 'info', 'annotated field info with values',
        constants.URL_CORPUS_TEST + '/fields/contents?listvalues=lemma');
expectUrlUnchanged('test', 'info', 'metadata field info with values',
        constants.URL_CORPUS_TEST + '/fields/title');

// Autocomplete
expectUrlUnchanged('test', 'info', 'autocomplete metadata field',
        constants.URL_CORPUS_TEST + '/autocomplete/title?term=a');
expectUrlUnchanged('test', 'info', 'autocomplete annotated field',
        constants.URL_CORPUS_TEST + '/autocomplete/contents/lemma?term=b');
