import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"BlackLab documentation","description":"","frontmatter":{"heroText":"BlackLab Hero","tagline":"Hero subtitle","actionText":"Get Started →","actionLink":"/blacklab/","features":[{"title":"Simplicity First","details":"Minimal setup with markdown-centered project structure helps you focus on writing."},{"title":"Vue-Powered","details":"Enjoy the dev experience of Vue + webpack, use Vue components in markdown, and develop custom themes with Vue."},{"title":"Performant","details":"VuePress generates pre-rendered static HTML for each page, and runs as an SPA once a page is loaded."}],"footer":"MIT Licensed | Copyright © 2018-present Evan You"},"headers":[],"relativePath":"structure.md","filePath":"_structure.md","lastUpdated":1749114403000}');
const _sfc_main = { name: "structure.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="blacklab-documentation" tabindex="-1">BlackLab documentation <a class="header-anchor" href="#blacklab-documentation" aria-label="Permalink to &quot;BlackLab documentation&quot;">​</a></h1> <h2 id="new-indexing-structure" tabindex="-1">New Indexing structure <a class="header-anchor" href="#new-indexing-structure" aria-label="Permalink to &quot;New Indexing structure&quot;">​</a></h2> <ul><li><p>how to index</p> <ul><li>indextool
<ul><li>faster indexing</li></ul></li> <li>index using BLS</li></ul></li> <li><p>document formats</p> <ul><li>built-in formats</li> <li>a simple custom format</li> <li>xpath support level</li> <li>processing values</li> <li>allow viewing documents</li> <li>automatic xslt generation</li> <li>additional index metadata</li> <li>reducing index size</li> <li>extend existing formats</li> <li>annotated configuration file</li></ul></li> <li><p>non-xml input files</p> <ul><li>indexing CSV/TSV files</li> <li>indexing plain text files</li> <li>indexing Word, PDF, etc.</li></ul></li> <li><p>annotations</p> <ul><li>case- and diacritics sensitivity</li> <li>multiple values at one position</li> <li>indexing raw xml</li> <li>standoff annotations</li> <li>subannotations</li></ul></li> <li><p>metadata</p> <ul><li>embedded metadata</li> <li>external metadata</li> <li>fixed metadata value</li> <li>tokenized vs untokenized</li> <li>default gui widgets</li></ul></li></ul> <h2 id="overall-structure" tabindex="-1">Overall structure <a class="header-anchor" href="#overall-structure" aria-label="Permalink to &quot;Overall structure&quot;">​</a></h2> <p>This is the intended structure of the new BlackLab documentation:</p> <ul><li><p>Try</p> <ul><li>What is it?</li> <li>Who is it for?</li> <li>Features</li> <li>Quick Start for evaluating BlackLab Server and Frontend
<ul><li>Docker</li> <li>Tomcat</li></ul></li> <li>Test Index</li> <li>Search your corpus</li> <li>Advanced: BlackLab Corpus Query Language</li></ul></li> <li><p>BlackLab Server, a webservice</p> <ul><li>What is it?</li> <li>Who is it for?</li> <li>Basic example</li> <li>Getting started
<ul><li>Basic installation, configuration
<ul><li>Docker</li> <li>Tomcat</li></ul></li> <li>Indexing data
<ul><li>IndexTool</li> <li>via the webservice</li></ul></li> <li>Manual use in the browser</li> <li>Using BLS from different languages</li></ul></li> <li>REST API reference</li> <li>Detailed configuration</li> <li>Tutorials / howtos
<ul><li>User authentication, creating indices and adding data</li> <li>Convert/Tag plugins</li></ul></li></ul></li> <li><p>BlackLab Frontend, a web application</p> <ul><li>What is it?</li> <li>Who is it for?</li> <li>Getting started
<ul><li>Basic installation, configuration
<ul><li>Docker</li> <li>Tomcat</li></ul></li> <li>Using the application</li></ul></li> <li>UI reference</li> <li>Detailed configuration</li> <li>Tutorials / howtos
<ul><li>Customizing functionality</li></ul></li></ul></li> <li><p>BlackLab Corpus Query Language</p></li> <li><p>Developers</p> <ul><li><p>BlackLab Core, the Java library</p> <ul><li>Basic example</li></ul></li> <li><p>Tutorials / howtos</p> <ul><li>A custom analysis script</li> <li>Using the forward index</li> <li>Using capture groups</li> <li>Indexing a different input format</li></ul></li> <li><p>Customization</p> <ul><li>Implementing a custom query language</li> <li>Implementing a custom DocIndexer</li> <li>Implementing a Convert/Tag plugin</li></ul></li> <li><p>API Reference</p></li> <li><p>Internals</p> <ul><li>File formats</li></ul></li></ul></li></ul></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("_structure.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _structure = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  _structure as default
};
