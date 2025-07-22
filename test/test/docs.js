"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectUrlUnchanged } = require("./compare-responses");


/**
 * Test that a hits search for a pattern returns the correct number of hits and docs,
 * and optionally test that the first hit matches (either JSON or text).
 *
 * @param testName name of the test
 * @param params parameters to send, or single CQL pattern
 * @param filter (optional) if previous argument is a CQL pattern, this may be the document filter query
 */
function expectDocsUnchanged(testName, params, filter) {

    if (typeof params === 'string')
        params = { patt: params };
    if (typeof filter === 'string')
        params.filter = filter;

    const crit = [];
    if (params.patt)
        crit.push(`pattern ${params.patt}`);
    if (params.filter)
        crit.push(`filter ${params.filter}`);

    describe(`docs/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(constants.URL_PREFIX + '/docs')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid",
                wordsaroundhit: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged('docs', testName, res.body);
                done();
            });
        });
    });
}

// Test that all hits are fetched before the document result is created!
expectDocsUnchanged('any token', '[]');
expectDocsUnchanged('single word she', '"she"');

// Pattern-only docs search
expectDocsUnchanged('single word they', '"they"');

// Filter-only docs search
expectDocsUnchanged('filter only', { filter: 'pid:PBsve435' });

// Combined docs search
expectDocsUnchanged('pattern and filter', '"the"', 'pid:PBsve435');

// Doc metadata, contents
expectUrlUnchanged('docs', 'document metadata',
        constants.URL_PREFIX + '/docs/PBsve430');
expectUrlUnchanged('docs', 'document contents',
        constants.URL_PREFIX + '/docs/PBsve430/contents?patt=%22the%22', 'application/xml');

// Doc snippet
expectUrlUnchanged('docs', 'document snippet wordstart',
        constants.URL_PREFIX + '/docs/PBsve430/snippet?wordstart=5&wordend=15');
expectUrlUnchanged('docs', 'document snippet hitstart',
        constants.URL_PREFIX + '/docs/PBsve430/snippet?hitstart=3&hitend=5&wordsaroundhit=2');

// Doc facets
expectUrlUnchanged('docs', 'document facets',
        constants.URL_PREFIX + '/docs/?number=0&facets=field:title');

// Docs CSV
expectUrlUnchanged('docs', 'CSV results',
        constants.URL_PREFIX + '/docs/', 'text/csv');
