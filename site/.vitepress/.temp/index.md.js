import { ssrRenderAttrs } from "vue/server-renderer";
import { useSSRContext } from "vue";
import { _ as _export_sfc } from "./plugin-vue_export-helper.1tPrXgE0.js";
const __pageData = JSON.parse('{"title":"","description":"","frontmatter":{"layout":"home","hero":{"name":"BlackLab corpus search","text":"Powerful search for large, annotated text corpora","tagline":"Publish, search, and analyze your corpora with speed and flexibility","actions":[{"theme":"brand","text":"Learn more →","link":"/guide/"},{"theme":"alt","text":"Or go the Frontend docs →","link":"https://blacklab-frontend.ivdnt.org/"}]},"features":[{"title":"Publish your corpora","details":"Many common formats supported. Easily configure your own."},{"title":"Find your matches","details":"Rich querying for token annotations or dependency relations."},{"title":"Fast and scalable","details":"Uses Apache Lucene as a solid foundation."}]},"headers":[],"relativePath":"index.md","filePath":"index.md","lastUpdated":1749114403000}');
const _sfc_main = { name: "index.md" };
function _sfc_ssrRender(_ctx, _push, _parent, _attrs, $props, $setup, $data, $options) {
  _push(`<div${ssrRenderAttrs(_attrs)}></div>`);
}
const _sfc_setup = _sfc_main.setup;
_sfc_main.setup = (props, ctx) => {
  const ssrContext = useSSRContext();
  (ssrContext.modules || (ssrContext.modules = /* @__PURE__ */ new Set())).add("index.md");
  return _sfc_setup ? _sfc_setup(props, ctx) : void 0;
};
const index = /* @__PURE__ */ _export_sfc(_sfc_main, [["ssrRender", _sfc_ssrRender]]);
export {
  __pageData,
  index as default
};
