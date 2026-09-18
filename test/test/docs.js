"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
chai.use(chaiHttp);

const constants = require('./constants');
const { expectUnchanged, expectCorpusUrlUnchanged } = require("./compare-responses");
const {corpusUrl} = require("./util");
const parseXml = require('xml2js').parseStringPromise;


/**
 * Test that a hits search for a pattern returns the correct number of hits and docs,
 * and optionally test that the first hit matches (either JSON or text).
 *
 * @param corpusName name of the corpus
 * @param testName name of the test
 * @param params parameters to send, or single CQL pattern
 * @param filter (optional) if previous argument is a CQL pattern, this may be the document filter query
 */
function expectDocsUnchanged(corpusName, testName, params, filter) {

    if (typeof params === 'string')
        params = { patt: params };
    if (typeof filter === 'string')
        params.filter = filter;

    describe(`${corpusName}/docs/${testName}`, () => {
        it('response should match previous', done => {
            chai.request(constants.SERVER_URL)
            .get(corpusUrl(corpusName) + '/docs')
            .query({
                api: constants.TEST_API_VERSION,
                sort: "field:pid",
                context: 1,
                waitfortotal: "true",
                //usecache: "no", // causes the search to be executed multiple times (hits, count, etc.)
                ...params
            })
            .set('Accept', 'application/json')
            .end((err, res) => {
                expect(err).to.be.null;
                expect(res).to.have.status(200);
                expectUnchanged(corpusName, 'docs', testName, res.body);
                done();
            });
        });
    });
}

let corpus = 'test';

// Test that all hits are fetched before the document result is created!
expectDocsUnchanged(corpus, 'any token', '[]');
expectDocsUnchanged(corpus, 'single word she', '"she"');

// Pattern-only docs search
expectDocsUnchanged(corpus, 'single word they', '"they"');

// Filter-only docs search
expectDocsUnchanged(corpus, 'filter only', { filter: 'pid:PBsve435' });

// Combined docs search
expectDocsUnchanged(corpus, 'pattern and filter', '"the"', 'pid:PBsve435');

// Doc metadata, contents
expectCorpusUrlUnchanged(corpus, 'docs', 'document metadata',
        '/docs/PBsve430');
expectCorpusUrlUnchanged(corpus, 'docs', 'document contents',
        '/docs/PBsve430/contents?patt=%22the%22', 'application/xml');

const describeSource = constants.INDEX_TYPE === 'solr' ? describe.skip : describe;
describeSource('original XML contents', () => {
    const doc = constants.URL_CORPUS_TEST + '/docs/PBsve430';
    const request = (endpoint, query, format = 'json') => chai.request(constants.SERVER_URL)
        .get(doc + endpoint).query({ ...query, outputformat: format });

    it('returns the same document contents in JSON', async () => {
        const json = await request('/contents', { patt: '"the"' });
        expect(json).to.have.status(200);
        expect(Object.keys(json.body)).to.deep.equal(['contents']);
        expectUnchanged(constants.CORPUS_TEST, 'docs', 'document contents', json.body.contents);
    });

    it('returns complete XML for bounded contents without a response metadata envelope', async () => {
        const res = await request('/contents', { wordstart: 7, wordend: 12 });
        expect(res).to.have.status(200);
        expect(Object.keys(res.body)).to.deep.equal(['contents']);
        expect(res.body.contents).not.to.contain('<hl');
        const xml = await parseXml(res.body.contents);
        expect(xml.blacklabResponse.text).to.have.lengthOf(1);
    });

    it('defaults contents to XML for existing XSLT clients', async () => {
        const res = await chai.request(constants.SERVER_URL).get(doc + '/contents').query({ patt: '"the"' });
        expect(res).to.have.status(200);
        expect(res.headers['content-type']).to.contain('xml');
        expectUnchanged(constants.CORPUS_TEST, 'docs', 'document contents', res.text || res.body);
    });

    it('directs structural original snippets to the contents endpoint', async () => {
        const res = await request('/snippet', { hitstart: 9, hitend: 10, context: 2, usecontent: 'orig' });
        expect(res).to.have.status(400);
        expect(res.body.error.code).to.equal('UNSUPPORTED_CONCORDANCE_REPRESENTATION');
    });
});

// Doc snippet
expectCorpusUrlUnchanged(corpus, 'docs', 'document snippet wordstart',
        '/docs/PBsve430/snippet?wordstart=5&wordend=15');
expectCorpusUrlUnchanged(corpus, 'docs', 'document snippet hitstart',
        '/docs/PBsve430/snippet?hitstart=3&hitend=5&context=2');

// Doc facets
expectCorpusUrlUnchanged(corpus, 'docs', 'document facets', '/docs/?number=0&facets=field:title');

// Docs CSV
expectCorpusUrlUnchanged(corpus, 'docs', 'CSV results', '/docs/', 'text/csv');

// Some tests on the corpus with fragment metadata (should return full docs)

corpus = 'fragments';
expectDocsUnchanged(corpus, 'single word the', '"the"');
expectDocsUnchanged(corpus, 'both docs', { filter: 'author:Jan author:Gene' });
expectDocsUnchanged(corpus, 'frag by field', { filter: 'author:Jan' });
expectDocsUnchanged(corpus, 'frag by id', { filter: 'pid:doc-01-frag-02' });

expectDocsUnchanged(corpus, 'pattern and filter', '"one"', 'year:1987');

expectCorpusUrlUnchanged(corpus, 'docs', 'document metadata (fragments1)', '/docs/doc-01');
