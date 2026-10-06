"use strict";
const chai = require("chai");
const chaiHttp = require("chai-http");
chai.use(chaiHttp);

const { expectUrlUnchanged } = require("./compare-responses");

expectUrlUnchanged('test', 'input-formats', 'list input formats', '/input-formats/');
expectUrlUnchanged('test', 'input-formats', 'voice-tei input format', '/input-formats/voice-tei');
