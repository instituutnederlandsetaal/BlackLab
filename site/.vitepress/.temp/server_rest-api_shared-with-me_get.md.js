import { ssrRenderAttrs, ssrRenderStyle } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"Corpora shared with me","description":"","frontmatter":{},"headers":[],"relativePath":"server/rest-api/shared-with-me/get.md","filePath":"server/030_rest-api/_shared-with-me/get.md","lastUpdated":1749114403000}');
const _sfc_main = { name: "server/rest-api/shared-with-me/get.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}><h1 id="corpora-shared-with-me" tabindex="-1">Corpora shared with me <a class="header-anchor" href="#corpora-shared-with-me" aria-label="Permalink to &quot;Corpora shared with me&quot;">​</a></h1> <p>Get the list of corpora that have been shared with the currently logged-in user (on this BlackLab Server instance).</p> <p>Authentication and user corpora must be enabled.</p> <p><strong>URL</strong> : <code>/blacklab-server/shared-with-me</code></p> <p><strong>Method</strong> : <code>GET</code></p> <h2 id="success-response" tabindex="-1">Success Response <a class="header-anchor" href="#success-response" aria-label="Permalink to &quot;Success Response&quot;">​</a></h2> <p><strong>Code</strong> : <code>200 OK</code></p> <h3 id="content-examples" tabindex="-1">Content examples <a class="header-anchor" href="#content-examples" aria-label="Permalink to &quot;Content examples&quot;">​</a></h3> <div class="language-json vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">json</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">{</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#005CC5", "--shiki-dark": "#79B8FF" })}">  &quot;corpora&quot;</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">: [</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">    &quot;my-friend@example.com:their-corpus-name&quot;</span><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">,</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#032F62", "--shiki-dark": "#9ECBFF" })}">    &quot;someone-else@example:com:another-fine-corpus&quot;</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">  ]</span></span>
<span class="line"><span style="${ssrRenderStyle({ "--shiki-light": "#24292E", "--shiki-dark": "#E1E4E8" })}">}</span></span></code></pre></div></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("server/030_rest-api/_shared-with-me/get.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const get = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  get as default
};
