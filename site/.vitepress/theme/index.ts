// https://vitepress.dev/guide/custom-theme
import { type Theme, inBrowser } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import { enhanceAppWithTabs } from 'vitepress-plugin-tabs/client'
import RedirectingLayout from './Redirect.vue'

import FileTree from './FileTree.vue'

import './style.scss'

const linkTitles = {
  'ivdnt.org': 'Dutch Language Institute',
  'blacklab-frontend.ivdnt.org': 'BlackLab Frontend documentation',
  'github.com/instituutnederlandsetaal/blacklab': 'BlackLab on GitHub',
};

export default {
  ...DefaultTheme,
  Layout: RedirectingLayout,
  enhanceApp({ app, router, siteData }) {
    app.component('FileTree', FileTree)
    enhanceAppWithTabs(app);
    // ...

    // Add title attributes to navigation links
    if (inBrowser) {
      // @ts-expect-error
      setTimeout(() => { // wait for navbar to be rendered
        // @ts-expect-error
        const navLinks = document.querySelectorAll('.VPNavBarMenuLink, .VPNavBarTitle a, .VPSocialLink');
        navLinks.forEach((link: HTMLLinkElement) => {
          // Add title based on the link text
          const url = link.href.trim().replace(/https?:\/\/([^\/].*[^\/])\/?/, '$1');
          if (!link.title && url in linkTitles) {
            // @ts-ignore
            link.title = linkTitles[url];
          }
        });
      }, 100);
    }

  }
} satisfies Theme
