"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectUrlUnchanged, sanitizeResponse} = require("./compare-responses");
const { corpusUrl } = require("./util");

/**
 * Test that a hits search returns the same response as before.
 *
 * @param corpusName name of the corpus.
 * @param testName name of the test (and file name of the expected response).
 * @param params search parameters.
 */
function expectHitsUnchanged(corpusName, testName, params) {

    // You can call this function with one string parameter, which is then used
    // as both the name and the CQL pattern.
    if (params === undefined)
        throw 'Please pass both a test name and CQL pattern (or parameter object)';

    // You can specify a CQL pattern or a map of parameters
    if (typeof params === 'string')
        params = { patt: params };

    describe(`${corpusName}/hits/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(corpusUrl(corpusName) + '/hits')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid,hitposition", // fully defined sort
                context: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged(corpusName, 'hits', testName, res.body);
                done();
            });
        });
    });
}

// Single word
expectHitsUnchanged("test", "single word the", '"the"');
expectHitsUnchanged("test", "pattern and filter", { patt: '"the"', filter: 'pid:PBsve430' });
expectHitsUnchanged("test", "simple phrase a succesful", '"a" [lemma="successful"]');
// Also test that forward index matching either the first or the second clause produces the same results
expectHitsUnchanged("test", "phrase a succesful with fimatch 1st", '_fimatch("a", [lemma="successful"], 0)');
expectHitsUnchanged("test", "phrase a succesful with fimatch 2nd", '_fimatch("a", [lemma="successful"], 1)');

// Simple capture group
expectHitsUnchanged("test", "simple capture group", '"one" A:[]');
expectHitsUnchanged("test", "same hit, different captures", '"one" A:([]{1,2}) []{1,2}');

// A few simpler tests, just checking matching text
expectHitsUnchanged("test", "any token", '[]');
expectHitsUnchanged("test", "two-four-single-regex", '"two|four"');
expectHitsUnchanged("test", "two-four-separate", '"two"|"four"');
expectHitsUnchanged("test", "token level AND", '[lemma="be" & word="are"]');
expectHitsUnchanged("test", "token level AND NOT", '[lemma="be" & word!="are"]');
expectHitsUnchanged("test", "containing", '<u/> containing "good"');
expectHitsUnchanged("test", "within", '[word="very"] [word="good"] within <u/>');

// View a single group from grouped hits
expectHitsUnchanged('test', 'view single group', {
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:service encounter about visa application for family members',
});

// Matching doc facets
expectUrlUnchanged('test', 'hits', 'document facets',
        corpusUrl('test') + '/hits/?patt=%22the%22&number=0&facets=field:pid');

// Hits CSV
expectUrlUnchanged('test', 'hits', 'CSV results',
        corpusUrl('test') + '/hits/?patt=%22the%22', 'text/csv');

// /termfreq operation
expectUrlUnchanged('test', 'hits', 'Termfreq word sensitive',
        corpusUrl('test') + '/termfreq/?annotation=word&sensitive=true');
expectUrlUnchanged('test', 'hits', 'Termfreq lemma insensitive',
        corpusUrl('test') + '/termfreq/?annotation=lemma');


expectHitsUnchanged("fragments", "hits in fragments", { patt: '"the"', filter: 'year:1900' });
expectHitsUnchanged("fragments", "hit in adjacent fragments", { patt: '"Lindenlaan" "this"', filter: 'year:1976 OR year:2026' });
expectHitsUnchanged("fragments", "hit beyond matching fragment", { patt: '"Lindenlaan" "this"', filter: 'year:1976' });
expectHitsUnchanged("fragments", "inherited metadata", { patt: '"dog"', filter: 'author:Piet' });
