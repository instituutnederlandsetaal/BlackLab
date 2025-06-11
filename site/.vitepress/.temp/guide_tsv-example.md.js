import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse(`{"title":"","description":"Contains examples that we link to sometimes, but aren't in the main doc navs","frontmatter":{"excludeFromSidebar":true,"description":"Contains examples that we link to sometimes, but aren't in the main doc navs"},"headers":[],"relativePath":"guide/tsv-example.md","filePath":"guide/tsv-example.md","lastUpdated":1749114403000}`);
const _sfc_main = { name: "guide/tsv-example.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><p>Below is an example of a TSV file that will be parsed correctly by the builtin &#39;tsv&#39; input format.</p> <p><strong>NOTE:</strong> the whitespace between words <strong>MUST</strong> be tab characters.</p> <p>The first line contains the column names: word, lemma and pos. This line must be at the top of your file too (also lowercase). (The order of the columns may differ from the example below if you wish)</p> <div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>word	lemma	pos</span></span>
<span class="line"><span>The	the	ART</span></span>
<span class="line"><span>quick	quick	ADJ</span></span>
<span class="line"><span>brown	brown	ADJ</span></span>
<span class="line"><span>fox	fox	N</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("guide/tsv-example.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const tsvExample = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  tsvExample as default
};
