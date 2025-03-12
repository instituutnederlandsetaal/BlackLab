import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'

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
    /*
    TODO: fix this
    "plugins": {
        "@vuepress/plugin-html-redirect": {
            "countdown": 0
        }
    }*/
});
