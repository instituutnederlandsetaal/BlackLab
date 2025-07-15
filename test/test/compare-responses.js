"use strict";
const chai = require("chai");
const expect = chai.expect;
const fs = require('fs')
const path = require('path')
const sanitizeFileName = require("sanitize-filename");
const stableStringify = require('json-stable-stringify'); // for diffable output

const constants = require('./constants');
const SAVED_RESPONSES_PATH =  constants.SAVED_RESPONSES_PATH;
const LATEST_TEST_OUTPUT_PATH = constants.LATEST_TEST_OUTPUT_PATH;

/**
 * Remove variable values such as build time, version, etc. from response.
 *
 * This enables us to compare responses from tests.
 *
 * @param response response to sanitize
 * @return response with variable values replaces with fixed ones
 */
function sanitizeBlsResponse(response) {
    const keysToMakeConstant = {
        // Server information page
        apiVersion: true, //'DELETE',
        blacklabBuildTime: true,
        blacklabVersion: true,
        corpora: { // API v4/5
            test: {
                timeModified: true
            }
        },
        indices: { // API v3/4
            test: {
                timeModified: true
            }
        },
        cacheStatus: 'DELETE',

        // Corpus information page
        versionInfo: {
            blacklabBuildTime: true, // API v4
            blacklabVersion: true,   // API v4
            blackLabBuildTime: true, // API v3 (inconsistent casing)
            blackLabVersion: true,   // API v3 (inconsistent casing)
            indexFormat: true,
            timeCreated: true,
            timeModified: true
        },
        metadataFields: {
            fromInputFile: {
                fieldValues: true    // dir names may differ, ignore
            }
        },

        // Hits/docs response
        summary: {
            searchTime: true,
            countTime: true
        },

        // Top-level timeModified key on index status page (e.g. /test/status/)
        timeModified: true
    };

    const transformValues = (value, key) => {
        if (key === 'displayName' && value === 'Starttag') {
            // Renamed to _relation in integrated index format
            return '_relation';
        }
        if (key === 'fromInputFile' && typeof value === 'string') {
            // Strip directory from fromInputFile field values
            return value.replace(/^.*[/\\]([^/\\]+)$/, "$1");
        }
        return value;
    };

    const transformKeys = (key) => {
        // starttag annotation has been renamed to _relation in integrated index format
        return key === 'starttag' ? '_relation' : key;
    }

    return sanitizeResponse(response, keysToMakeConstant, transformValues, transformKeys);
}

/**
 * Clean up a response object for comparison.
 *
 * Replaces specified values in JSON object structure with constant ones,
 * and optionally applies a function to apply to values in the response.
 *
 * This enables us to transform responses into a form that we can easily compare.
 *
 * @param response response to clean up
 * @param keysToMakeConstant (potentially nested) object structure of keys that, when found, should be set to a
 *   constant value. Note that only the keys of this object are used. Alternatively, a single string or a list of
 *   strings is also allowed. If null or undefined are specified, an empty object is used.
 * @param transformValueFunc (optional) function to apply to all values copied from the response (i.e. values not
 *   being made constant)
 * @param transformKeyFunc (optional) function to apply to all keys
 * @return sanitized response
 */
function sanitizeResponse(response, keysToMakeConstant, transformValueFunc = ((v, k = undefined) => v),
                          transformKeyFunc = ((k, _) => k )) {

    if (Array.isArray(response)) {
        // Process each element in the array recursively
        return response.map(v => sanitizeResponse(v, keysToMakeConstant, transformValueFunc, transformKeyFunc));
    } else if (!(typeof response === 'object')) {
        // Regular value (probably an array element); just call the transform function and return
        return transformValueFunc(response);
    }

    // Make sure keysToMakeConstant is an object
    let recursive = false;
    if (Array.isArray(keysToMakeConstant)) {
        keysToMakeConstant = Object.fromEntries(keysToMakeConstant.map(v => [v, true]));
    } else if (typeof keysToMakeConstant === 'object') {
        recursive = true;
    } else if (typeof keysToMakeConstant === 'string') {
        keysToMakeConstant = { [keysToMakeConstant]: true };
    } else {
        keysToMakeConstant = {};
    }

    // Replace any of the keys from keysToMakeConstant with constant values,
    // and perform any other fixes if fixValueFunc was supplied.
    const cleanedData = {};
    for (let origKey in response) {
        const value = response[origKey];
        const key = transformKeyFunc(origKey, value);
        if (keysToMakeConstant.hasOwnProperty(key)) {
            // This is (or contains) a variable value we don't want to compare.
            if (recursive && typeof keysToMakeConstant[key] === 'object' && typeof value === 'object' && !Array.isArray(value)) {
                // Subobject; recursively fix this part of the response
                cleanedData[key] = sanitizeResponse(value, keysToMakeConstant[key], transformValueFunc, transformKeyFunc);
            } else {
                // Single value or array. Delete or make fixed value
                if (keysToMakeConstant[key] !== 'DELETE') {
                    cleanedData[key] = "VALUE_REMOVED";
                }
            }
        } else {
            // No values to make constant, just regular values we want to compare.
            if (Array.isArray(value)) {
                // Call ourselves to process the array
                // Note that we apply transformValueFunc on the result again so we can pass the origKey for the array,
                // otherwise origKey-specific rules won't work.
                cleanedData[key] = value.map(v => transformValueFunc(sanitizeResponse(v, {}, transformValueFunc, transformKeyFunc), key));
            } else if (typeof value === 'object') {
                // Object; call ourselves recursively to sanitize it
                cleanedData[key] = sanitizeResponse(value, {}, transformValueFunc, transformKeyFunc);
            } else {
                // Regular value; call transform function.
                cleanedData[key] = transformValueFunc(value, key);
            }
        }
    }
    return cleanedData;
}

/**
 * Either save this response if it's the first time we
 * run this test, or compare it to the previously saved version.
 *
 * @param category test category (e.g. "hits")
 * @param testName name of this test, and file name for the response
 * @param actualResponse webservice response we got (parsed JSON)
 */
function expectUnchanged(category, testName, actualResponse) {
    if (Buffer.isBuffer(actualResponse))
        actualResponse = actualResponse.toString();
    const isJson = typeof actualResponse === 'object';
    const sanCategory = sanitizeFileName(category);
    const sanFileName = sanitizeFileName(testName);

    // Remove anything that's variable (e.g. search time) from the response.
    const sanitized = isJson ? sanitizeBlsResponse(actualResponse) : actualResponse;

    // Stringify to JSON if we're saving the response to a file
    let toSave = '';
    let saveTestOutputFile = '(test results)';
    if (process.env.BLACKLAB_TEST_SAVE_MISSING_RESPONSES === 'true') {
        toSave = isJson ? stableStringify(sanitized, { space: 2 }) : sanitized;

        // Write to latest test path so we can compare (and easily update) in case of changes.
        if (!fs.existsSync(LATEST_TEST_OUTPUT_PATH))
            fs.mkdirSync(LATEST_TEST_OUTPUT_PATH);
        const categoryDir = path.resolve(LATEST_TEST_OUTPUT_PATH, sanCategory);
        if (!fs.existsSync(categoryDir))
            fs.mkdirSync(categoryDir);

        saveTestOutputFile = path.resolve(LATEST_TEST_OUTPUT_PATH, sanCategory, `${sanFileName}.json`);
        fs.writeFileSync(saveTestOutputFile, toSave, {encoding: 'utf8'});
    }

    // Ensure category dir exists
    const categoryDir = path.resolve(SAVED_RESPONSES_PATH, sanCategory);
    if (!fs.existsSync(categoryDir))
        fs.mkdirSync(categoryDir);

    // Did we have a previous response?
    const savedResponseFile = path.resolve(SAVED_RESPONSES_PATH, sanCategory, `${sanFileName}.json`);
    if (fs.existsSync(savedResponseFile)) {
        // Read previously saved response to compare
        const fileContents = fs.readFileSync(savedResponseFile, { encoding: 'utf8' });
        const savedResponse = isJson ? JSON.parse(fileContents) : fileContents;

        // Compare
        console.log(`Comparing saved response ${savedResponseFile} to current response ${saveTestOutputFile}`);
        if (isJson) {
            expect(sanitized).to.be.deep.equal(savedResponse);
        } else {
            expect(sanitized.trim()).to.be.equal(savedResponse.trim());
        }

        /*
        // DEBUG. Overwrite our new stable JSON response, which we know is identical.
        if (process.env.BLACKLAB_TEST_SAVE_MISSING_RESPONSES === 'true') {
            // Save this response for subsequent tests
            fs.writeFileSync(savedResponseFile, json, {encoding: 'utf8'});
        }*/

    } else {
        if (process.env.BLACKLAB_TEST_SAVE_MISSING_RESPONSES === 'true') {
            // Save this response for subsequent tests
            fs.writeFileSync(savedResponseFile, toSave, {encoding: 'utf8'});
        } else {
            expect.fail(`Response for ${category}/${testName} not found. Make sure it exists (use run-local.sh to save responses)`);
        }
    }
}

function expectUrlUnchanged(category, testName, url, expectedType = 'application/json') {
    const params = url.indexOf('api=') >= 0 ? undefined : { api: constants.TEST_API_VERSION };
    describe(`${category}/${testName}`, () => {
        it('response should match previous', done => {
            const get = chai
                    .request(constants.SERVER_URL)
                    .get(url);
            //console.log(`URL=${url}, params=${JSON.stringify(params)}`);
            const query = params ? get.query(params) : get;
            query
                    .set('Accept', expectedType)
                    .end((err, res) => {
                        if (err)
                            done(err);

                        expect(res, 'response').to.have.status(200);
                        expectUnchanged(category, testName, res.body);
                        done();
                    });
        });
    });
}

module.exports = {
    sanitizeResponse,
    sanitizeBlsResponse,
    expectUnchanged,
    expectUrlUnchanged,
};
