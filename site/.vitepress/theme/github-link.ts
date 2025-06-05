import * as MarkdownIt from 'markdown-it';
import * as fs from 'fs';
import * as path from 'path';

export interface GithubLinkPluginOptions {
  organisation: string;
  repository: string;
  branch: string;
  /** Used to check for dead links. Use an absolute path here! */
  projectRoot: string;
}
/**
 * Custom Markdown-It plugin to convert links starting with @github: to GitHub links.
 * Usage: [my markdown link](@github:/path/from/project/root/file.js)
 * 
 * @param options the github org/user, repository, branch, and project root directory
 * @returns 
 */
export default function createGithubLinkPlugin(options: GithubLinkPluginOptions) {
  return function githubLinkPlugin(md: MarkdownIt) {
    const defaultRender = md.renderer.rules.link_open || function(tokens, idx, options, env, self) {
      return self.renderToken(tokens, idx, options);
    };

    md.renderer.rules.link_open = function(tokens, idx, options_, env, self) {
      const hrefIndex = tokens[idx].attrIndex('href');
      if (hrefIndex >= 0) {
        const href = tokens[idx].attrs![hrefIndex][1];
        if (href.startsWith('@github:')) {
          const filePath = href.replace('@github:', '').replace(/^\//, ''); // Remove leading slash if present
          const absPath = path.resolve(options.projectRoot, filePath); // Ensure absolute path is correct
          if (fs.existsSync(absPath)) {
            const githubBase = `https://github.com/${options.organisation}/${options.repository}/blob/${options.branch}/`;
            tokens[idx].attrs![hrefIndex][1] = githubBase + filePath;
          } else {
            tokens[idx].attrs![hrefIndex][1] = '#file-not-found';
            // Add a warning tooltip
            tokens[idx].attrSet('title', `Source file not found: ${filePath}`);
            // Optionally, mark the link visually (e.g., with a CSS class)
            tokens[idx].attrJoin('class', 'github-link-not-found');
            // Log a warning in the console with absolute, relative file paths, and the markdown file path if available
            const mdFile = env && env.filePath ? env.filePath : 'unknown markdown file';
            // eslint-disable-next-line no-console
            console.warn(`GithubLinkPlugin: Source file not found: ${absPath} (source: ${filePath}) in markdown: ${mdFile}`);
          }
        }
      }
      return defaultRender(tokens, idx, options_, env, self);
    };
  };
}