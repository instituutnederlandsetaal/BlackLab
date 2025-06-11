import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Future plans","description":"","frontmatter":{},"headers":[],"relativePath":"guide/future-plans.md","filePath":"guide/060_future-plans.md","lastUpdated":1749114403000}');
const _sfc_main = { name: "guide/future-plans.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="future-plans" tabindex="-1">Future plans <a class="header-anchor" href="#future-plans" aria-label="Permalink to &quot;Future plans&quot;">​</a></h1> <p>Broadly speaking, these are our plans for the future:</p> <ul><li>Improve performance and memory requirements (ongoing)</li> <li>Keep up with new Lucene versions (ongoing)</li> <li>Scale to larger corpora by adding support for distributed search,
through integration with Solr (in progress)</li> <li>Keep up to date with new Java versions (ongoing)</li> <li>Make accessing and highlighting the original content of documents more flexible (stored in the index or accessed through a web service)</li> <li>Enhance dependency relations search</li> <li>Integrate improvements and suggestions from the community (<a href="https://github.com/instituutnederlandsetaal/BlackLab/issues" target="_blank" rel="noreferrer">issues</a> and <a href="https://github.com/instituutnederlandsetaal/BlackLab/pulls" target="_blank" rel="noreferrer">pull requests</a> welcome!)</li></ul> <p>For more (somewhat technical) details, see the corresponding <a href="https://github.com/instituutnederlandsetaal/BlackLab/tree/dev/doc/bark#readme" target="_blank" rel="noreferrer">BARKs</a> (short descriptions of planned development and other information).</p></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("guide/060_future-plans.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const _060_futurePlans = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  _060_futurePlans as default
};
