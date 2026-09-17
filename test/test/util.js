"use strict";

const constants = require('./constants');

function toBeDocInfo(expectDocInfo) {
    expectDocInfo.to.be.an("object").that.has.all.keys(
        "fromInputFile", "lengthInTokens", "mayView", "pid", "title"
    );
}

function toBeContextPart(expectPart) {
    expectPart.to.be.an("object").that.has.all.keys(
        'punct',
        'lemma',
        'pos',
        'word'
    );
}

function corpusUrl(corpusName) {
    return `${constants.URL_PREFIX}/${corpusName}`;
}

module.exports = {
    toBeDocInfo,
    toBeContextPart,
    corpusUrl,
};
