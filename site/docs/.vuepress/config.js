import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'
import { redirectPlugin } from '@vuepress/plugin-redirect'

export default defineUserConfig({
    bundler: viteBundler(),

    "base": "/BlackLab/",
    "title": "BlackLab",
    "description": "Documentation for the corpus search engine BlackLab",

    theme: defaultTheme({
        "smoothScroll": true,
        colorModeSwitch: false,
        "navbar": [
            {
                "text": "Guide",
                "link": "/guide/"
            },
            {
                "text": "Webservice",
                "link": "/server/"
            },
            {
                "text": "Frontend",
                "link": "/frontend/"
            },
            {
                "text": "Developers",
                "link": "/development/"
            }
        ],
        "repo": "INL/BlackLab",
        "lastUpdated": "Last Updated",
        "sidebar": {
            "/guide/": [
                {
                    link: "",
                    text: "Introduction"
                },
                "getting-started",
                "indexing-with-blacklab",
                "how-to-configure-indexing",
                "corpus-query-language",
                {
                    link: "faq",
                    text: "FAQ"
                },
                "future-plans",
                "who-uses-blacklab",
                "about"
            ],
            "/server/": [
                "",
                {
                    link: "overview",
                    text: "How to use"
                },
                {
                    link: "rest-api/",
                    text: "API reference"
                },
                "from-different-languages",
                "configuration",
                "howtos"
            ],
            "/frontend/": [
                ""
            ],
            "/development/": [
                "",
                "downloads",
                "examples/example-application",
                "query-tool",
                {
                    "text": "Customization",
                    "children": [
                        "customization/docindexer",
                        "customization/query-language",
                        "customization/plugins",
                        "customization/legacy-docindexers"
                    ]
                },
                "solr/",
                "migration-guide",
                "changelog",
                {
                    "text": "API redesign",
                    "children": [
                        "api-redesign/",
                        "api-redesign/API"
                    ]
                }
            ],
            "/": [
                ""
            ]
        },
        "nextLinks": true,
        "prevLinks": true,
        "docsDir": "site/docs",
        "docsBranch": "dev",
        "editLinks": true,
        "editLinkText": "Help us improve this page!"
    }),

    "plugins": [
        redirectPlugin({
            config: {
                '/BlackLab/add-input-format.html': '/BlackLab/guide/how-to-configure-indexing.html',
                '/BlackLab/blacklab-in-action.html': '/BlackLab/guide/#try-it-online',
                '/BlackLab/blacklab-internals.html': '/BlackLab/development/#internals',
                '/BlackLab/blacklab-server-different-languages.html': '/BlackLab/server/from-different-languages.html',
                '/BlackLab/blacklab-server-overview.html': '/BlackLab/server/',
                '/BlackLab/blog.html': '/BlackLab/development/archive/blog.html',
                '/BlackLab/changelog.html': '/BlackLab/development/changelog.html',
                '/BlackLab/configuration-files.html': '/BlackLab/server/configuration.html',
                '/BlackLab/corpus-query-language.html': '/BlackLab/guide/corpus-query-language.html',
                '/BlackLab/corpus-search-engine.html': '/BlackLab/guide',
                '/BlackLab/downloads.html': '/BlackLab/development/downloads.html',
                '/BlackLab/example-application.html': '/BlackLab/development/examples/example-application.html',
                '/BlackLab/faq.html': '/BlackLab/guide/faq.html',
                '/BlackLab/features.html': '/BlackLab/guide/#features',
                '/BlackLab/file-formats.html': '/BlackLab/development/#internals',
                '/BlackLab/getting-started.html': '/BlackLab/guide/getting-started.html',
                '/BlackLab/how-to-configure-indexing.html': '/BlackLab/guide/how-to-configure-indexing.html',
                '/BlackLab/improve-search-speed.html': '/BlackLab/server/vmtouch.html',
                '/BlackLab/indexing-with-blacklab.html': '/BlackLab/guide/indexing-with-blacklab.html',
                '/BlackLab/install-macos.html': '/BlackLab/server/install-macos.html',
                '/BlackLab/learn.html': '/BlackLab/guide',
                '/BlackLab/migration-guide.html': '/BlackLab/development/migration-guide.html',
                '/BlackLab/newsletter.html': '/BlackLab/development/archive/newsletter.html',
                '/BlackLab/plugins.html': '/BlackLab/development/customization/plugins.html',
                '/BlackLab/query-tool.html': '/BlackLab/development/query-tool.html',
                '/BlackLab/roadmap.html': '/BlackLab/guide/future-plans.html',
                '/BlackLab/tsv-example.html': '/BlackLab/guide/tsv-example.html',
                '/BlackLab/who-uses-blacklab.html': '/BlackLab/guide/who-uses-blacklab.html',
                '/BlackLab/xpath_examples.html': '/BlackLab/guide/xpath-examples.html',
                '/add-input-format.html': '/guide/how-to-configure-indexing.html',
                '/blacklab-in-action.html': '/guide/#try-it-online',
                '/blacklab-internals.html': '/development/#internals',
                '/blacklab-server-different-languages.html': '/server/from-different-languages.html',
                '/blacklab-server-overview.html': '/server/',
                '/blog.html': '/development/archive/blog.html',
                '/changelog.html': '/development/changelog.html',
                '/configuration-files.html': '/server/configuration.html',
                '/corpus-query-language.html': '/guide/corpus-query-language.html',
                '/corpus-search-engine.html': '/guide',
                '/downloads.html': '/development/downloads.html',
                '/example-application.html': '/development/examples/example-application.html',
                '/faq.html': '/guide/faq.html',
                '/features.html': '/guide/#features',
                '/file-formats.html': '/development/#internals',
                '/getting-started.html': '/guide/getting-started.html',
                '/how-to-configure-indexing.html': '/guide/how-to-configure-indexing.html',
                '/improve-search-speed.html': '/server/vmtouch.html',
                '/indexing-with-blacklab.html': '/guide/indexing-with-blacklab.html',
                '/install-macos.html': '/server/install-macos.html',
                '/learn.html': '/guide',
                '/migration-guide.html': '/development/migration-guide.html',
                '/newsletter.html': '/development/archive/newsletter.html',
                '/plugins.html': '/development/customization/plugins.html',
                '/query-tool.html': '/development/query-tool.html',
                '/roadmap.html': '/guide/future-plans.html',
                '/tsv-example.html': '/guide/tsv-example.html',
                '/who-uses-blacklab.html': '/guide/who-uses-blacklab.html',
                '/xpath_examples.html': '/guide/xpath-examples.html'
            }
        })
    ]
});
