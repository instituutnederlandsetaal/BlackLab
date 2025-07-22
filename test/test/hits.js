"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const should = chai.should();
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectUrlUnchanged, sanitizeResponse} = require("./compare-responses");

/**
 * Test that a hits search returns the same response as before.
 *
 * @param testName The name of the test (and file name of the expected response).
 * @param params The search parameters.
 */
function expectHitsUnchanged(testName, params) {

    // You can call this function with one string parameter, which is then used
    // as both the name and the CQL pattern.
    if (params === undefined)
        throw 'Please pass both a test name and CQL pattern (or parameter object)';

    // You can specify a CQL pattern or a map of parameters
    if (typeof params === 'string')
        params = { patt: params };

    describe(`hits/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(constants.URL_PREFIX + '/hits')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid,hitposition", // fully defined sort
                wordsaroundhit: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged('hits', testName, res.body);
                done();
            });
        });
    });
}

// Single word
expectHitsUnchanged("single word the", '"the"');
expectHitsUnchanged("simple phrase a succesful", '"a" [lemma="successful"]');
// Also test that forward index matching either the first or the second clause produces the same results
expectHitsUnchanged("phrase a succesful with _FI1", '_FI1("a", [lemma="successful"])');
expectHitsUnchanged("phrase a succesful with _FI2", '_FI2("a", [lemma="successful"])');

// Simple capture group
expectHitsUnchanged("simple capture group", '"one" A:[]');
expectHitsUnchanged("same hit, different captures", '"one" A:([]{1,2}) []{1,2}');

// A few simpler tests, just checking matching text
expectHitsUnchanged("any token", '[]');
expectHitsUnchanged("two-four-single-regex", '"two|four"');
expectHitsUnchanged("two-four-separate", '"two"|"four"');
expectHitsUnchanged("token level AND", '[lemma="be" & word="are"]');
expectHitsUnchanged("token level AND NOT", '[lemma="be" & word!="are"]');
expectHitsUnchanged("containing", '<u/> containing "good"');
expectHitsUnchanged("within", '"very" "good" within <u/>');

// View a single group from grouped hits
expectHitsUnchanged('view single group', {
    patt: '"a"',
    group: 'field:title',
    viewgroup: 'str:service encounter about visa application for family members',
});

// Matching doc facets
expectUrlUnchanged('hits', 'document facets',
        constants.URL_PREFIX + '/hits/?patt=%22the%22&number=0&facets=field:pid');

// Hits CSV
expectUrlUnchanged('hits', 'CSV results',
        constants.URL_PREFIX + '/hits/?patt=%22the%22', 'text/csv');

// /termfreq operation
expectUrlUnchanged('hits', 'Termfreq word sensitive',
        constants.URL_PREFIX + '/termfreq/?annotation=word&sensitive=true');
expectUrlUnchanged('hits', 'Termfreq lemma insensitive',
        constants.URL_PREFIX + '/termfreq/?annotation=lemma');
