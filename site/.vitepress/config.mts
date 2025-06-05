import { defineConfig } from 'vitepress'
import { withSidebar } from 'vitepress-sidebar'
import { SidebarItem, VitePressSidebarOptions } from 'vitepress-sidebar/types';
import { tabsMarkdownPlugin } from 'vitepress-plugin-tabs'
import githubLinkPlugin from './theme/github-link'
import path from 'path';

/**
 * Remove leading numbers and separators (underscores or slashes) from each path segment.
 */
function stripNumbersAndUnderscoresFromLink(str: string | undefined): string | undefined {
  return str?.split('/').map(stripNumbersFromText).join('/');
}

function stripNumbersFromText(text: string | undefined): string | undefined {
  return text?.replace(/^[_\d]+/, '');
}

function capitalizeFirstLetterAndRemoveUndercores(str: string | undefined): string | undefined {
  str = str?.replace(/^_+/, '').replace(/_+/g, ' ');
  return str && (str.charAt(0).toUpperCase() + str.slice(1));
}

/**
 * vitepress-sidebar can strip numbers from the display, but not from the links themselves.
 * vitepress itself can strip numbers from the links, but not from the display.
 * 
 * When we enable both, we have good display names and good links, 
 * but the active state of the page in the sidebar is not set correctly.
 * So we need to fix up the links anyway...
 * 
 * @param config 
 * @returns 
 */
function stripNumbersFromLinksInSidebar(config: ReturnType<typeof defineConfig>): ReturnType<typeof defineConfig> {
  function processItems(items: SidebarItem[] | undefined): SidebarItem[] | undefined {
    return items?.map(item => ({
      ...item,
      link: stripNumbersAndUnderscoresFromLink(item.link),
      text: capitalizeFirstLetterAndRemoveUndercores(stripNumbersFromText(item.text)),
      items: processItems(item.items),
    }));
  }

  if (!config.themeConfig?.sidebar) return config;

  if (Array.isArray(config.themeConfig.sidebar)) { // SidebarItem[]
    config.themeConfig.sidebar = processItems(config.themeConfig.sidebar);
  } else { // Record<path, SidebarItem[]|{items, base}>
    config.themeConfig.sidebar = Object.fromEntries(Object.entries(config.themeConfig.sidebar).map(([path, item]) => [
      stripNumbersAndUnderscoresFromLink(path),
      Array.isArray(item) ? processItems(item) : {
        ...item,
        base: stripNumbersAndUnderscoresFromLink(item.base),
        items: processItems(item.items)
      }
    ]));
  }
  return config;
}

const baseSidebarConfig: VitePressSidebarOptions = {
  // Sidebar generator options
  useTitleFromFrontmatter: true, // precedence
  useTitleFromFileHeading: true, // secondary
  useFolderTitleFromIndexFile: true, // tertiary
  documentRootPath: 'docs', // needs to be set to the same as srcDir
  capitalizeFirst: true, // capitalize first letter of folder names (probably files too, but we set explicit titles)

  sortMenusByFrontmatterOrder: true, // "order: " in frontmatter
  excludePattern: ['_*'], // Do not show files and directories starting with an underscore in the sidebar. They are still compiled and can be linked to, but not shown in the sidebar.
  useFolderLinkFromIndexFile: true, // clicking folder entry in sidebar opens the index.md file

  collapseDepth: 1,
  includeRootIndexFile: true,

  excludeFilesByFrontmatterFieldName: 'excludeFromSidebar'
};

// https://vitepress.dev/reference/site-config
export default stripNumbersFromLinksInSidebar(defineConfig(withSidebar({
  title: "/ BlackLab /",
  description: "Documentation for the corpus search engine BlackLab",
  srcDir: 'docs',

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
    },
    // No other locales for now, but setup so that we can add them later
  },

  vue: {
    template: {
      compilerOptions: {
        whitespace: 'preserve',
      }
    }
  },

  markdown: {
    config(md) {
      md.use(tabsMarkdownPlugin);
      md.use(githubLinkPlugin({
        organisation: 'instituutnederlandsetaal',
        repository: 'blacklab',
        branch: 'dev',
        // absolute path to the project root
        projectRoot: path.resolve(__dirname, '../../'),
      }));
    },
  },

  lastUpdated: true,

  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config

    logo: '/img/ivdnt-logo-4regels.svg',
    nav: [
      { text: 'Guide', link: '/guide/' },
      { text: 'Webservice', link: '/server/' },
      { text: 'Developers', link: '/development/' },
      { text: 'Frontend', link: 'https://blacklab-frontend.ivdnt.org/' },
      { text: '/INT/', link: 'https://ivdnt.org/' }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/instituutnederlandsetaal/blacklab/' }
    ],

    search: {
      provider: 'local'
    },
    editLink: {
      pattern: `https://github.com/instituutnederlandsetaal/blacklab/edit/dev/site/docs/:path`,
    },
    footer: {
      message: 'Apache license 2.0',
      copyright: 'Copyright © 2010-present Dutch Language Institute',
    },
    lastUpdated: {}, // enabled, but defaults are fine. removing this line disables it
    docFooter: {
      next: false,
      prev: false,
    },

    outline: [2,3]
  },

  rewrites: id => stripNumbersAndUnderscoresFromLink(id)!,
  ignoreDeadLinks: 'localhostLinks' // some examples refer to localhost
}, [
  {scanStartPath: 'guide', resolvePath: '/guide/', ...baseSidebarConfig},
  {scanStartPath: 'development', resolvePath: '/development/', ...baseSidebarConfig},
  {scanStartPath: 'server', resolvePath: '/server/', ...baseSidebarConfig},
])))
