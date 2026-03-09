# Creating a BlackLab release

## New release version

We generally work on the `dev` branch (for the next planned release) or `maintenance` branch (for a point release for the last major version, while `dev` is already on the next major version).

To draft a new release, execute the following Bash commands:

```bash
# The branch to draft the release from (e.g. dev / maintenance)
export BL_RELEASE_FROM_BRANCH=dev

# The version to be released
export BL_RELEASE_VERSION=... # e.g. 5.0.0

# The next snapshot version to set after the release
export BL_SNAPSHOT_VERSION=... # e.g. 5.1.0-SNAPSHOT

# The tag to create for the release (e.g. v5.0.0)
export BL_RELEASE_TAG="v$BL_RELEASE_VERSION"

# If there's any uncommitted changes: test and commit them first
git checkout "$BL_RELEASE_FROM_BRANCH"
mvn clean package
test/run-local.sh
git commit

# Prepare release
vi src/site/markdown/changelog.md # update changelog
vi src/site/markdown/downloads.md # update version info if needed
scripts/set-version.sh "$BL_RELEASE_VERSION" # set Maven project version

# Create tag
git tag -a "$BL_RELEASE_TAG"

# Set branch to next SNAPSHOT version
scripts/set-version.sh "$BL_SNAPSHOT_VERSION"

# Push to GitHub
git push origin "$BL_RELEASE_FROM_BRANCH" "$BL_RELEASE_TAG"
# (Docker Hub will pick up the new tag and create an image for it automatically)
cd tools/target
zip -r blacklab-tools-$BL_RELEASE_VERSION.zip blacklab*.jar lib/
# Make GitHub release based on tag.
# Add blacklab-server-$BL_RELEASE_VERSION.war and blacklab-tools-$BL_RELEASE_VERSION.zip.
```

## Deploy new release to Maven Central

After doing the above to create the release on GitHub, deploy it to Maven Central:

```bash
# (from the top-level BlackLab repo dir)
git checkout "$BL_RELEASE_TAG"
mvn clean deploy -P release     # requires appropriate ~/.m2/settings.xml and signing GPG key
# log into https://central.sonatype.com/publishing and Publish the artifact just deployed
git checkout $BL_RELEASE_FROM_BRANCH  # (don't forget to switch back to your working branch)
```

## Update Maven project site

BlackLab’s website, https://blacklab.ivdnt.org/ is built using Vitepress. Run `site/deploy.sh` to build and upload the latest version. Obviously requires write access.
