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
function expectHitsGroupedUnchanged(testName, params) {
    const pattern = params.patt;
    const groupBy = params.group;
    const filteredBy  = params.filter ? `, filtered by ${params.filter}` : '';

    describe(`hits-grouped/${testName}`, () => {
        it('response should match previous', done => {
            chai
            .request(constants.SERVER_URL)
            .get(constants.URL_PREFIX + '/hits')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "size,identity",
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
                expectUnchanged('hits-grouped', testName, res.body);
                done();
            });
        });
    });
}


// Single word
expectHitsGroupedUnchanged('very grouped by word after',
        { patt: '"very"', group: 'after:word:i:1' });
expectHitsGroupedUnchanged('a grouped by title',
        { patt: '"a"', group: 'field:title' });

// Compare hit grouping with regular (HitGroupFromHits) and fast (HitGroupsTokenFrequencies) path.
// Results should be identical (hence the same test name)
expectHitsGroupedUnchanged('any token grouped by word',
        { patt: '[word != "abcdefg"]', group: 'hit:word:i'}); // regular path
expectHitsGroupedUnchanged('any token grouped by word 2',
        { patt: '[]', group: 'hit:word:i'}); // fast path

// Same comparison but with metadata filter
const filter = 'pid:PBsve430';
expectHitsGroupedUnchanged('any token grouped by word with filter',
        { patt: '[word != "abcdefg"]', filter, group: 'hit:word:i'}); // regular path
expectHitsGroupedUnchanged('any token grouped by word with filter 2',
        { patt: '[]', filter, group: 'hit:word:i'}); // fast path

// Group by capture
expectHitsGroupedUnchanged('group by capture',
        { patt: '"a|the" X:[]', filter, group: 'capture:word:i:X'});
