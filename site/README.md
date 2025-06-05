# BlackLab documentation site

These are the source files for the documentation of the BlackLab site.

There are a few things to keep in mind when adding documentation:

## Development

Install required libraries/tools, e.g. vitepress:

    npm install

Run the development server:

    npm run start

Build the production site:

    npm run docs:build

## Sidebar content 

The sidebar's entries are automatically generated based on the files in the `docs` directory, however you must restart the dev server to see changes.
There are a few things to take into account when naming files.

## Order of entries

Files are sorted in the sidebar according to the following rules:
- Frontmatter `order` field, if present.  
  Files/directories with an explicit `order` field will be put in bucket `0`.
- Files/directories in the same bucket are sorted by filename.

## Titles of entries
The first to be defined is used:
1. the frontmatter `title` field, if present.  
2. the first h1 heading in the file, if present.
3. the filename, without the extension, with leading numbers and underscores removed.

## URLS

URLS are simply the file path, minus leading numbers and underscores, and with the `.md` extension replaced by `.html`. When linking from document to document, you should not include a file extension.

E.g. `docs/guide/010_getting-started.md` becomes `/guide/getting-started.html`. For linking from another document, you would use `[Getting started](/guide/getting-started)`.
