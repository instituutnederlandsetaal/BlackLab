# BlackLab test suite

## What and why

This is a collection of tests for BlackLab Server. It will test the functions of BlackLab Server on a known test index, ensuring that the results are still as expected. The goal is to quickly discover when a change has broken some functionality.

This is a work in progress. Not all functionality is yet covered by a test.


## How to use

The easiest way to do this is to run this process in Docker; this automates the process and saves you from having to install (a specific version of) `npm` on your machine. From the top level of this repository run:

```bash
## Optionally point to a directory containing custom blacklab server configurations
#export CONFIG_ROOT=./custom-server-configuration

## Optionally point to a directory containing custom data, optionally including a custom environment for testing
#export INDEX_TEST_DATA_CONFIGURATION=./custom-data

sh test/run-ci.sh
```

If you add a new test, you should run `test/run-local.sh`. The new test will always PASS and the server response will be saved to `test/data/saved-responses/<category>/<testName>.json`. Then subsequent runs of `run-ci.sh` will use the saved response.

`run-local` is also useful if the test output changes (i.e. a key is added to the JSON output), because it will always save the latest results to the `test/data/latest-test-output` directory (which is excluded from Git). You can then use a diff tool to check the differences and update the saved response if necessary.

### Build a blacklab test server
If you've made changes ensure the latest BlackLab code and tests are being used by:

```bash
# Ensure testserver is recreated (if it changed)
docker compose up -d --build testserver

# Ensure the test image is up to date
docker compose build test
```

The blacklab server under test uses configurations files defined in the `docker` directory. If you would like to override the 
default configurations when building the `testserver` use the `CONFIG_ROOT` build argument pointing to
a directory containing:
- server.xml: will be used to configure tomcat
- blacklab-server.xml: will be used to configure blacklab

and build the test server again.

NOTE: `CONFIG_ROOT` must be a path residing *inside the repository file structure and relative to the root of the repository*

```bash
# PATH_TO_CUSTOM_CONFIG must contain at least two files: blacklab-server.xml and server.xml
CONFIG_ROOT=[PATH_TO_CUSTOM_CONFIG] docker compose up -d --build testserver
```

### Build and run the tests
Build the containers with the tests
```bash
## Build the tests container
docker compose build test
```
By default, it will use the data found the `test/data/input` directory for testing

Run the tests
```bash
# Run the tests
docker compose run --rm test
```

The tests should now be run and the output shown. If all tests succeed, `$?` should have the value `0`.

If you want to run the tests manually outside of Docker, you should:

- index the data in `test/data/input` to an index named `test` (using config `voice-tei.blf.yaml`)
- start a local server that can access this index
- run the tests using `npm run test` from the `test` directory

### Custom test configuration
BlackLab allows to configure the shape of the responses, to allow for the tests to account for different 
server configurations, the tests can also be configured via enviroment variables matching the server
configurations. Consult the [constants.js](test/constants.js) for the list of supported environment variables.

You can easily override the defaults by setting the environment them within a file name `environment` and building/running
the test docker container. See the [below section on custom data]

```bash
echo BLACKLAB_DEFAULT_WINDOW_SIZE=20 >> test/data/environment
docker compose build test
docker compose run --rm test
```

## Tools

This test suite is set up using Node.js, Mocha and Chai. Also provided is a Dockerfile, so the full test, from indexing data to testing search functionality, can be run inside containers. If you have Docker installed, it is therefore not necessary to install Node on your machine.

Mocha/Chai were chosen because they are mature, popular and tests are easy to understand and write. We also use these tools on other projects for user interface testing (with Cypress).


## Test data

### Default data
We currently use a small sample of data from the [VOICE](https://www.univie.ac.at/voice/) (Vienna-Oxford International Corpus of English) project. The test data consists of lemmatized and PoS-tagged TEI, making it easy to index in BlackLab. Using English also makes it easier for others to read and write tests.

The test data is included in our repository (in the `test/data/input` subdirectory) because the tests rely on this specific data, so it is important that they can be distributed together.

Data from the VOICE project is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License](http://creativecommons.org/licenses/by-nc-sa/3.0/). As BlackLab is noncommercial and licensed under the Apache license, we believe that including this test data conforms to this license. If you are the rightsholder and believe otherwise, please let us know.

### Custom data

You can customize the testing data used for **indexing tests** by setting the `TEST_DATA_ROOT` to a directory
containing test data configuration files. `TEST_DATA_ROOT` must available inside the repository and
relative to the `test/` directory.

In addition, if the `TEST_DATA_ROOT` directory contains a file named `environment`. The [test script](perform-test-run.sh)
will source it before running any tests. This allows the test to add environment variables to control for custom blacklab server configurations.

For guidance on how to configure custom test data see the example here: [index-test-config.json](data/index-test-config.json)


## GithubActions to run tests on pull requests and merges

The blacklab repository automates pull request checks via github actions jobs.
There are currently two workflows ensuring the quality of the pull requests:

- [Build and unit tests](../.github/workflows/maven.yml): builds the code change and runs all blacklab unit tests via maven.
- [Integration tests](../.github/workflows/integration-test.yml): builds the code change and runs [integration tests](./test).
  Note: Integrations tests are currently running on two different data sets, [the default data set](./test/data/input) and a 
  custom configured data set whose settings are recorded on a per-repository basis.
