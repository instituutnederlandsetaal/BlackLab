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
                expectUnchanged('info', 'Server info page', res.body);
                done();
            });
    });
});

// Server info
expectUrlUnchanged('info', 'server', '/'); // ?api=exp&custom=true
expectUrlUnchanged('info', 'input formats', '/input-formats');

// Corpus info
expectUrlUnchanged('info', 'corpus', '/test/');
expectUrlUnchanged('info', 'corpus status', '/test/status');

// Field info with list of values
expectUrlUnchanged('info', 'annotated field info with values',
        '/test/fields/contents?listvalues=lemma');
expectUrlUnchanged('info', 'metadata field info with values',
        '/test/fields/title');

// Autocomplete
expectUrlUnchanged('info', 'autocomplete metadata field',
        '/test/autocomplete/title?term=a');
expectUrlUnchanged('info', 'autocomplete annotated field',
        '/test/autocomplete/contents/lemma?term=b');
