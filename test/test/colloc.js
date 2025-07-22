"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged } = require("./compare-responses");

// Test that a hits search for a pattern returns the correct number of hits and docs,
// and optionally test that the first hit matches (either JSON or text).
function expectCollocUnchanged(testName, params) {
    if (typeof params === 'string')
        params = { patt: params };

    describe(`/hits?calc=colloc with pattern ${params.patt}`, () => {
        it('should return expected response (#hits/docs, structure)', done => {
            chai.request(constants.SERVER_URL)
            .get(constants.URL_PREFIX + '/hits')
            .query({
                api: constants.TEST_API_VERSION,
                calc: 'colloc',
                wordsaroundhit: 10,
                sensitive: 'false',
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged('colloc', testName, res.body);
                done();
            });
        });
    });
}


// Single word
expectCollocUnchanged('single word the', '"the"');

// Phrase
expectCollocUnchanged('phrase', '"a" []');
