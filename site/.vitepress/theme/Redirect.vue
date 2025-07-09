<script setup lang="ts">
const redirects = Object.entries({
  '/BlackLab/blog.html':                                 '/archive/blog',
  '/BlackLab/development/archive/blog.html':             '/archive/blog',
  '/blog.html':                                          '/archive/blog',
  '/development/archive/blog.html':                      '/archive/blog',
  '/BlackLab/development/archive/newsletter.html':       '/archive/newsletter',
  '/BlackLab/newsletter.html':                           '/archive/newsletter',
  '/development/archive/newsletter.html':                '/archive/newsletter',
  '/newsletter.html':                                    '/archive/newsletter',
  '/blacklab-internals.html':                            '/development/#internals',
  '/BlackLab/blacklab-internals.html':                   '/development/#internals',
  '/BlackLab/development/#internals':                    '/development/#internals',
  '/BlackLab/file-formats.html':                         '/development/#internals',
  '/file-formats.html':                                  '/development/#internals',
  '/BlackLab/changelog.html':                            '/development/changelog.html',
  '/BlackLab/development/changelog.html':                '/development/changelog.html',
  '/changelog.html':                                     '/development/changelog.html',
  '/BlackLab/development/customization/plugins.html':    '/development/customization/plugins.html',
  '/BlackLab/plugins.html':                              '/development/customization/plugins.html',
  '/plugins.html':                                       '/development/customization/plugins.html',
  '/BlackLab/development/downloads.html':                '/development/downloads.html',
  '/BlackLab/downloads.html':                            '/development/downloads.html',
  '/downloads.html':                                     '/development/downloads.html',
  '/example-application.html':                           '/development/examples/example-application.html',
  '/BlackLab/development/migration-guide.html':          '/development/migration-guide.html',
  '/BlackLab/migration-guide.html':                      '/development/migration-guide.html',
  '/migration-guide.html':                               '/development/migration-guide.html',
  '/BlackLab/development/query-tool.html':               '/development/query-tool.html',
  '/BlackLab/query-tool.html':                           '/development/query-tool.html',
  '/query-tool.html':                                    '/development/query-tool.html',
  '/BlackLab/corpus-search-engine.html':                 '/guide',
  '/BlackLab/guide':                                     '/guide',
  '/BlackLab/learn.html':                                '/guide',
  '/corpus-search-engine.html':                          '/guide',
  '/learn.html':                                         '/guide',
  '/BlackLab/features.html':                             '/guide/#features',
  '/BlackLab/guide/#features':                           '/guide/#features',
  '/features.html':                                      '/guide/#features',
  '/blacklab-in-action.html':                            '/guide/#try-it-online',
  '/BlackLab/blacklab-in-action.html':                   '/guide/#try-it-online',
  '/BlackLab/guide/#try-it-online':                      '/guide/#try-it-online',
  '/BlackLab/corpus-query-language.html':                '/guide/query-language/',
  '/BlackLab/guide/corpus-query-language.html':          '/guide/query-language/',
  '/corpus-query-language.html':                         '/guide/query-language/',
  '/guide/corpus-query-language.html':                   '/guide/query-language/',
  '/BlackLab/development/examples/example-application.': '/guide/example-application.html',
  '/BlackLab/example-application.html':                  '/guide/example-application.html',
  '/BlackLab/faq.html':                                  '/guide/faq.html',
  '/BlackLab/guide/faq.html':                            '/guide/faq.html',
  '/faq.html':                                           '/guide/faq.html',
  '/BlackLab/guide/future-plans.html':                   '/guide/future-plans.html',
  '/BlackLab/roadmap.html':                              '/guide/future-plans.html',
  '/roadmap.html':                                       '/guide/future-plans.html',
  '/BlackLab/getting-started.html':                      '/guide/getting-started.html',
  '/BlackLab/guide/getting-started.html':                '/guide/getting-started.html',
  '/getting-started.html':                               '/guide/getting-started.html',
  '/add-input-format.html':                              '/guide/index-your-data/simple-example.html',
  '/BlackLab/add-input-format.html':                     '/guide/index-your-data/simple-example.html',
  '/BlackLab/guide/how-to-configure-indexing.html':      '/guide/index-your-data/simple-example.html',
  '/BlackLab/how-to-configure-indexing.html':            '/guide/index-your-data/simple-example.html',
  '/how-to-configure-indexing.html':                     '/guide/index-your-data/simple-example.html',
  '/guide/how-to-configure-indexing.html':               '/guide/index-your-data/simple-example.html',
  '/BlackLab/guide/indexing-with-blacklab.html':         '/guide/index-your-data/create-an-index.html',
  '/BlackLab/indexing-with-blacklab.html':               '/guide/index-your-data/create-an-index.html',
  '/indexing-with-blacklab.html':                        '/guide/index-your-data/create-an-index.html',
  '/guide/indexing-with-blacklab.html':                  '/guide/index-your-data/create-an-index.html',
  '/BlackLab/guide/tsv-example.html':                    '/guide/tsv-example.html',
  '/BlackLab/tsv-example.html':                          '/guide/tsv-example.html',
  '/tsv-example.html':                                   '/guide/tsv-example.html',
  '/BlackLab/guide/who-uses-blacklab.html':              '/guide/who-uses-blacklab.html',
  '/BlackLab/who-uses-blacklab.html':                    '/guide/who-uses-blacklab.html',
  '/who-uses-blacklab.html':                             '/guide/who-uses-blacklab.html',
  '/BlackLab/guide/xpath-examples.html':                 '/guide/xpath-examples.html',
  '/BlackLab/xpath_examples.html':                       '/guide/xpath-examples.html',
  '/xpath_examples.html':                                '/guide/xpath-examples.html',
  '/blacklab-server-overview.html':                      '/server/',
  '/BlackLab/blacklab-server-overview.html':             '/server/',
  '/BlackLab/server/':                                   '/server/',
  '/BlackLab/configuration-files.html':                  '/server/configuration.html',
  '/BlackLab/server/configuration.html':                 '/server/configuration.html',
  '/configuration-files.html':                           '/server/configuration.html',
  '/blacklab-server-different-languages.html':           '/server/from-different-languages.html',
  '/BlackLab/blacklab-server-different-languages.html':  '/server/from-different-languages.html',
  '/BlackLab/server/from-different-languages.html':      '/server/from-different-languages.html',
  '/BlackLab/install-macos.html':                        '/server/install-macos.html',
  '/BlackLab/server/install-macos.html':                 '/server/install-macos.html',
  '/install-macos.html':                                 '/server/install-macos.html',
  '/BlackLab/improve-search-speed.html':                 '/server/vmtouch.html',
  '/BlackLab/server/vmtouch.html':                       '/server/vmtouch.html',
  '/improve-search-speed.html':                          '/server/vmtouch.html',
  '/server/rest-api/get.html':                           '/server/rest-api/server-corpus-info/server-info.html',
  '/server/rest-api/corpus/get.html':                    '/server/rest-api/server-corpus-info/corpus-info.html',
  '/server/rest-api/corpus/hits/get.html':               '/server/rest-api/search/find-hits.html',
  '/server/rest-api/corpus/docs/pid/get.html':           '/server/rest-api/documents/metadata.html',
  '/server/howtos.html':                                 '/server/user-corpora.html',
});

import DefaultTheme from 'vitepress/theme'
import { inBrowser, useData, useRouter } from 'vitepress'
import { watch } from 'vue'

const { page } = useData()
const { go } = useRouter()

watch(
  () => page.value.isNotFound,
  (isNotFound) => {
    if (!isNotFound || !inBrowser) return
    const redirect = redirects.find(([from]) => window.location.pathname.startsWith(from))
    if (!redirect) return
    go(redirect[1] + window.location.pathname.slice(redirect[0].length))
  },
  { immediate: true }
)

// Example redirect test script, uncomment and run in the browser console: testRedirects(redirects);

/* 
// Required to hook into the router from the page console.
window.redirects = redirects;
window.routerGo = go;

async function testRedirects(redirects) {
  const failing = [];

  for (const [from, to] of Object.entries(redirects)) {
    if (redirects[to]) continue; // Skip if target is also a source
    
    window.routerGo(to);
    await new Promise(resolve => setTimeout(resolve, 1000)); // wait a second
    if (document.title.startsWith('404')) {
      failing.push(to);
    }
  }

  if (failing.length) {
    console.log('Failing URLs (404):', failing);
  } else {
    console.log('All tested redirects are OK!');
  }
}

*/
</script>

<template>
  <DefaultTheme.Layout />
</template>
