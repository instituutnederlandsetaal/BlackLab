"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged } = require("./compare-responses");

const describeParallel = constants.INDEX_TYPE === "solr" ? describe.skip : describe;

describeParallel('parallel/api5 adjusted alignment otherFields', () => {
    it('response should include aligned field fragments', done => {
        chai.request(constants.SERVER_URL)
        .get(constants.PARALLEL_URL_PREFIX + '/hits')
        .query({
            api: "5",
            patt: "[word='Dit'] =w=>en _",
            field: "contents__nl",
            adjusthits: "true",
            withspans: "true",
            context: "1",
            waitfortotal: "true",
            first: 0,
            number: 10
        })
        .set('Accept', 'application/json')
        .end((err, res) => {
            if (err)
                return done(err);

            expect(res).to.have.status(200);
            expect(res.body.hits).to.have.lengthOf(1);
            expect(res.body.hits[0]).to.have.nested.property('otherFields.contents__en');
            expect(res.body.hits[0].otherFields.contents__en).to.have.nested.property('match.word');
            expect(res.body.hits[0].otherFields.contents__en.match.word).to.deep.equal(["This"]);
            expectUnchanged('parallel', 'api5 adjusted alignment otherFields', res.body);
            done();
        });
    });
});
