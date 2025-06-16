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
function expectDocsGroupedUnchanged(testName, params) {
    const pattern = params.patt;
    const groupBy = params.group;
    const filteredBy  = params.filter ? `, filtered by ${params.filter}` : '';

    describe(`docs-grouped/${testName}`, () => {
        it('response should match previous', done => {
            chai
            .request(constants.SERVER_URL)
            .get('/test/docs')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid",
                wordsaroundhit: 1,
                number: 30,
                ...params,
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                // NOTE: we pass true to remove summary.searchParam, because we perform some different requests
                //   that should produce the same response.
                expectUnchanged('docs-grouped', testName, res.body, false);
                done();
            });
        });
    });
}

// Docs grouped
expectDocsGroupedUnchanged('a grouped by title', { patt: '"a"', group: 'field:title' });
expectDocsGroupedUnchanged('view single group', {
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:interview about conference experience and impressions of city'
});
