"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
const expect = chai.expect;
const assert = chai.assert;
const crypto = require('crypto')
const fs = require('fs')
const path = require('path')
const parseXmlString = require('xml2js').parseStringPromise;
chai.use(chaiHttp);

const { sanitizeResponse, sanitizeBlsResponse} = require("./compare-responses");
const constants = require('./constants');

const TEST_DATA_ROOT = constants.TEST_DATA_ROOT;
const TEST_CONFIG = JSON.parse(fs.readFileSync(path.resolve(TEST_DATA_ROOT, 'index-test-config.json'),
        { encoding: 'utf-8' }));

const INPUT_FORMAT_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['input-format']);
const DOC_TO_INDEX_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['docs-to-index']);
const EXPECTED_INDEX_CONTENT_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['expected-index']);
const EXPECTED_INDEX_METADATA_PATH = path.resolve(TEST_DATA_ROOT, TEST_CONFIG['expected-metadata']);

const READ_FILE_OPTS = { encoding: 'utf-8' };

function addDefaultHeaders(request) {
    request.auth(constants.BLACKLAB_USER, constants.BLACKLAB_PASSWORD)
    const allHeaders = {
        'X-Request-ID': crypto.randomBytes(8).toString('hex'),
    }

    for (let key in allHeaders) {
        request.set(key, allHeaders[key]);
    }
}

function createIndexName() {
    return "test-index-" + crypto.randomInt(10000).toString();
}

async function createInputFormat() {
    const formatName = path.basename(INPUT_FORMAT_PATH);
    const request = chai
            .request(constants.SERVER_URL)
            .post('/input-formats')
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
            .attach('data', fs.readFileSync(INPUT_FORMAT_PATH), formatName);
    addDefaultHeaders(request);
    return request;
}

async function createIndex(indexName) {
    const index_url = constants.BLACKLAB_USER + ":" + indexName
    const inputFormat = TEST_CONFIG['input-format'].split('.')[0];
    const request = chai
            .request(constants.SERVER_URL)
            .post('/')
            .query({
                'api': constants.TEST_API_VERSION,
                'name': index_url,
                'display': indexName,
                'format': constants.BLACKLAB_USER + ":" + inputFormat
            })
            .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function getIndexStatus(indexName) {
    const indexUrl = constants.BLACKLAB_USER + ":" + indexName
    const request = chai
            .request(constants.SERVER_URL)
            .get("/" + indexUrl + "/status")
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function addToIndex(indexName, payloadPath) {
    const indexUrl = constants.BLACKLAB_USER + ":" + indexName
    const request = chai
            .request(constants.SERVER_URL)
            .post('/' + indexUrl + '/docs')
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
            .attach('data', fs.readFileSync(payloadPath), 'testdocs');
    addDefaultHeaders(request);
    return request;
}

async function getIndexContent(indexName) {
    const indexUrl = constants.BLACKLAB_USER + ":" + indexName;
    let request = chai
            .request(constants.SERVER_URL)
            .get("/" + indexUrl + "/docs")
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

function clearKeys(keys, data) {
    return sanitizeResponse(data, keys, (value, key) => {
        return key === 'displayName' && value === 'Starttag' ? '_relation' : value;

        // Is the value an array with a single string element that only contains whitespace?
        // If so, return an empty array. Otherwise, return unchanged
        //return Array.isArray(v) && v.length === 1 && typeof v[0] === "string" && v[0].trim() === "" ? [] : v;
    }, (key) => {
        // starttag annotation has been renamed to _relation in integrated index format
        return key === 'starttag' ? '_relation' : key;
    });
}

async function getIndexMetadata(indexName) {
    const indexUrl = constants.BLACKLAB_USER + ":" + indexName
    const request = chai
            .request(constants.SERVER_URL)
            .get("/" + indexUrl + "/")
            .query({ api: constants.TEST_API_VERSION })
            .set('Accept', 'application/json')
    addDefaultHeaders(request);
    return request.send();
}

async function xmlToJson(xml) {
    return parseXmlString(xml, null);
}

function queryIndex(indexName, pattern, filters, format = 'application/json') {
    const indexUrl = constants.BLACKLAB_USER + ":" + indexName + "/hits";
    const respFormat = format === "" ? "application/json" : format;
    const request = chai
            .request(constants.SERVER_URL)
            .post("/" + indexUrl + "/")
            .query({ api: constants.TEST_API_VERSION })
            .buffer()
            .set('Accept', respFormat)
    if (filters !== "") {
        request.query({"patt": pattern, "filter": filters})
    } else {
        request.query({"patt": pattern})
    }
    addDefaultHeaders(request);
    request.parse((res, callback) => {
        res.text = '';
        res.on('data', (chk) => {
            res.text += chk;
        });
        res.on('end', () => callback(null, res.text));
    });
    return request.send()
}

if (process.env.SKIP_INDEXING_TESTS !== 'true') { // Solr doesn't implement these functions yet

    describe('Indexing tests', () => {
        it('create a new index', async () => {
            const indexName = createIndexName();
            const respFormat = await createInputFormat();
            assert.isTrue(respFormat.ok, 'Error creating input format');

            const createRes = await createIndex(indexName);
            assert.isTrue(createRes.ok, 'Error creating index');

            const resGetIndex = await getIndexStatus(indexName);
            assert.isTrue(resGetIndex.ok, 'Error getting index status');
        });
        it('adds to index', async () => {

            const indexName = createIndexName();
            const req = await createInputFormat();
            assert.isTrue(req.ok, 'Error creating input format');

            const createRes = await createIndex(indexName);
            assert.isTrue(createRes.ok, 'Error creating index');

            const addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
            assert.equal(200, addReq.status, `Received code ${addReq.status} adding to index`);
            assert.isTrue(addReq.ok, 'Error adding to index');

            const indexContents = await getIndexContent(indexName);
            assert.isTrue(indexContents.ok, 'Error getting index contents');

            const body = indexContents.body;
            const expectedContent = JSON.parse(fs.readFileSync(EXPECTED_INDEX_CONTENT_PATH, READ_FILE_OPTS));

            const keys = ['summary', 'searchTime'];
            expect(clearKeys(keys, expectedContent)).to.be.deep.equal(clearKeys(keys, body));
        }).timeout(3000); // allow a little more time for slower servers

        it('get index metadata', async () => {
            const indexName = createIndexName();
            const req = await createInputFormat();
            assert.isTrue(req.ok, 'Error creating input format');

            let createRes = await createIndex(indexName);
            assert.isTrue(createRes.ok, 'Error creating index');

            let addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
            assert.equal(200, addReq.status, `Received code ${addReq.status} adding to index`);
            assert.isTrue(addReq.ok, 'Error adding to index');

            const indexMetadata = await getIndexMetadata(indexName);
            const body = indexMetadata.body;
            assert.isTrue(indexMetadata.ok, 'Error getting index metadata');

            const expectedMetadata = JSON.parse(fs.readFileSync(EXPECTED_INDEX_METADATA_PATH, READ_FILE_OPTS));

            const keys = ['indexName', 'displayName', 'versionInfo', 'documentFormat']
            expect(clearKeys(keys, expectedMetadata)).to.be.deep.equal(clearKeys(keys, body));
        });

        it('query from config', async () => {
            const indexName = createIndexName();
            const req = await createInputFormat();
            assert.isTrue(req.ok, 'Error creating input format');

            let createRes = await createIndex(indexName);
            assert.isTrue(createRes.ok, 'Error creating index');

            let addReq = await addToIndex(indexName, DOC_TO_INDEX_PATH);
            assert.equal(200, addReq.status, `Received code ${addReq.status} adding to index`);
            assert.isTrue(addReq.ok, 'Error adding to index');

            const queriesTest = TEST_CONFIG['queries'];
            for (let [testName, testCase] of Object.entries(queriesTest)) {
                const filterTerms = testCase['filters'];
                const queryInd = await queryIndex(indexName, '"120"', filterTerms, "application/xml")
                assert.isTrue(queryInd.ok, 'Error querying index');
                const body = await xmlToJson(queryInd.body);

                const key = 'summary';
                if (testCase['expected'] === null) {
                    const results = clearKeys(key, body['blacklabResponse']);
                    expect(results['hits']).to.be.empty;
                } else {
                    const expectedOutput = await xmlToJson(fs.readFileSync(path.resolve(TEST_DATA_ROOT, testCase['expected'])));
                    expect(clearKeys(key, expectedOutput['blacklabResponse'])).to.be.deep.equal(clearKeys(key, body['blacklabResponse']));
                }
            }
        });
    });
}
