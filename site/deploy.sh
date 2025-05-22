#!/usr/bin/env sh

# Based on https://kaizendorks.github.io/2020/04/16/vuepress-github-actions/

# abort on errors
set -e

# build
npm run build

# navigate into the build output directory
cd docs/.vuepress/dist

# Configure GitHub Pages to server the site at blacklab.ivdnt.org
echo blacklab.ivdnt.org > CNAME

# create new git repo from scratch with a single commit containing the generated files
git init
git add -A
git commit -m 'Deploy BlackLab site.'

# Force push to the "publishing source" of your GitHub pages site
# in this case, the gh-pages branch
git push -f git@github.com:INL/BlackLab.git main:gh-pages

# Back to previous directory (the root of your repo)
cd -
