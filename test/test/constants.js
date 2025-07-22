"use strict";
const SERVER_URL = process.env.APP_URL || "http://localhost:8080/blacklab-server";
const BLACKLAB_USER = process.env.BLACKLAB_USER || "user";
const BLACKLAB_PASSWORD = process.env.BLACKLAB_USER || "";

const TEST_DATA_ROOT = process.env.TEST_DATA_ROOT || "data";

// Determine where test responses are saved
// NOTE: if we're testing another index type, e.g. integrated, use responses from corresponding subdir
const INDEX_TYPE = process.env.INDEX_TYPE || "classic-external"; // what index type are we testing? (separate responses)
const OPT_INDEX_TYPE_PATH = INDEX_TYPE === "classic-external" ? "" : `-${INDEX_TYPE}`;

// Where are the "gold standard" test responses saved?
const SAVED_RESPONSES_BASE_PATH = process.env.SAVED_RESPONSES_PATH || `${TEST_DATA_ROOT}/saved-responses`;
const SAVED_RESPONSES_PATH = `${SAVED_RESPONSES_BASE_PATH}${OPT_INDEX_TYPE_PATH}`

// We always save test output when  BLACKLAB_TEST_SAVE_MISSING_RESPONSES is true?
// Convenient for checking and updating test responses after a run. (default to .gitignored dir)
const LATEST_TEST_OUTPUT_BASE_PATH = process.env.LATEST_TEST_OUTPUT_BASE_PATH || `${TEST_DATA_ROOT}/latest-test-output`;
const LATEST_TEST_OUTPUT_PATH = `${LATEST_TEST_OUTPUT_BASE_PATH}${OPT_INDEX_TYPE_PATH}`;

const TEST_API_VERSION = undefined; // (if undefined, automatically uses current stable version)

const URL_PREFIX = '/corpora/test';

module.exports = {
    SERVER_URL,
    BLACKLAB_USER,
    BLACKLAB_PASSWORD,
    TEST_DATA_ROOT,
    SAVED_RESPONSES_PATH,
    LATEST_TEST_OUTPUT_PATH,
    TEST_API_VERSION,
    URL_PREFIX
};
