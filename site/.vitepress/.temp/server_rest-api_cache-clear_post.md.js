import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Clear results cache","description":"","frontmatter":{},"headers":[],"relativePath":"server/rest-api/cache-clear/post.md","filePath":"server/030_rest-api/_cache-clear/post.md","lastUpdated":1749114403000}');
const _sfc_main = { name: "server/rest-api/cache-clear/post.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="clear-results-cache" tabindex="-1">Clear results cache <a class="header-anchor" href="#clear-results-cache" aria-label="Permalink to &quot;Clear results cache&quot;">​</a></h1> <p><strong>URL</strong> : <code>/blacklab-server/&lt;corpus-name&gt;/cache-clear</code></p> <p><strong>Method</strong> : <code>POST</code></p> <p>Only available in debug mode.</p> <h2 id="success-response" tabindex="-1">Success Response <a class="header-anchor" href="#success-response" aria-label="Permalink to &quot;Success Response&quot;">​</a></h2> <p><strong>Code</strong> : <code>200 OK</code></p> <h3 id="content-examples" tabindex="-1">Content examples <a class="header-anchor" href="#content-examples" aria-label="Permalink to &quot;Content examples&quot;">​</a></h3> <div class="language-json vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">json</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">{</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">  &quot;code&quot;</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;SUCCESS&quot;</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">,</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">  &quot;message&quot;</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: </span><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">&quot;Cache cleared succesfully.&quot;</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">}</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("server/030_rest-api/_cache-clear/post.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const post = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  post as default
};
