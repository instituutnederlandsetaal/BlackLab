import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"BCQL","description":"","frontmatter":{},"headers":[],"relativePath":"guide/query-language/index.md","filePath":"guide/040_query-language/005_index.md","lastUpdated":1749639304000}');
const _sfc_main = { name: "guide/query-language/index.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="bcql" tabindex="-1">BCQL <a class="header-anchor" href="#bcql" aria-label="Permalink to &quot;BCQL&quot;">​</a></h1> <p>BlackLab Corpus Query Language or BCQL is a powerful query language for text corpora.</p> <p>It is a dialect of the <a href="http://cwb.sourceforge.net/files/CQP_Tutorial/" title="http://cwb.sourceforge.net/files/CQP_Tutorial/" target="_blank" rel="noreferrer">CQP Query Language</a> introduced by the IMS Corpus WorkBench (CWB). Several other corpus engines support a similar language, such as the <a href="https://www.sketchengine.co.uk/documentation/corpus-querying/" title="https://www.sketchengine.co.uk/documentation/corpus-querying/" target="_blank" rel="noreferrer">Lexicom Sketch Engine</a>. The various dialects are very similar, but differ in some of the more advanced features.</p> <p>This section documents the various ways to use BCQL to query your data:</p> <ul><li><a href="./token-based.html">Token-based querying</a>: the most common way to query corpora, where you search for specific words or patterns in the text.</li> <li><a href="./relations.html">Relations querying</a>: query (dependency) relations between words or spans of text.</li> <li><a href="./parallel.html">Parallel querying</a>: querying your parallel corpora, finding alignments between different languages or historical versions of a text.</li> <li><a href="./miscellaneous.html">Miscellaneous</a>: other information about BCQL, such as operator precedence, features and comparisons with other corpus engines.</li></ul></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("guide/040_query-language/005_index.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _005_index = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  _005_index as default
};
