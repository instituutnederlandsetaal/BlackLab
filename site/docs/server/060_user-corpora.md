# User-managed corpora

If you configure a form of user authentication, you can allow users to manager their own corpora using BlackLab Server or Frontend.

BlackLab Server includes support for creating corpora and adding documents to them. We use these features in BlackLab Frontend to allow users to quickly corpus data and search it, without having to set up a BlackLab installation themselves. Here's a very quick overview.

Currently, only private corpora can be created and appended to. This means there must be a logged-in user. The [`authentication`](configuration.md#authentication) section in `blacklab-server.yaml` will let you specify what authentication system you'd like to use.

Another required setting is [`userIndexes`](/server/configuration.html#corpora-locations) (in addition to `indexLocations` which points to the "globally available" corpora). In this directory, user-private corpora will be created. Obviously, the application needs write permissions on this directory.

When a user is logged in and you have a `userIndexes` directory set up, you will see a `user` section on the BlackLab Server info page (`/blacklab-server/`) with both `loggedIn` and `canCreateIndex` set to `true`. To see what input formats are supported, look at the `/blacklab-server/input-formats/` URL.

To create a private corpus, `POST` to `/blacklab-server/` with parameters `name` (corpus identifier), `display` (a human-friendly corpus name) and `format` (the input format to use for this corpus, e.g. `tei`). The userId will be prepended to the corpus name, so if your userId is `myUserId` and you create an corpus name `myIndex`, the full name will be `myUserId:myIndex`.

To add a file to a private corpus, upload it to `/blacklab-server/INDEX_NAME/docs` with parameter name `data`.

To remove a private corpus, send a `DELETE` request to `/blacklab-server/INDEX_NAME/`.

See [Manage user corpora API endpoints](/server/rest-api/corpus-management/) for details.

## Manage user input formats

To add an input format, upload a `.yaml` or `.json` configuration file to the `/blacklab-server/input-formats/` URL with parameter name `data`. The file name will become the format name. User formats will be prefixed with the `userId` and a colon, so if your userId is `myUserId` and you upload a file `myFormatName.blf.yaml`, a new format `myUserId:myFormatName` will be created. Only you will see it in the formats list, but in theory, everyone can use it (this is different from corpora, which are private).

To view an input format configuration, use `/blacklab-server/input-formats/<format-name>`.

To remove an input format, send a `DELETE` request to the format page, e.g. `/blacklab-server/input-formats/<format-name>`.

See [Input formats API endpoints](/server/rest-api/input-formats/) for details.

## Share a private corpus

To see what users (if any) a private corpus is currently shared with, use: `/blacklab-server/<corpus-name>/sharing`.

To set the list of users to share a private corpus with, send a `POST` request to the same URL with the `users[]` parameter for each user to share with (that is, you should specify this parameter multiple times, once for each user). You can leave the parameter empty if you don't want to share the corpus anymore.

See [Set sharing settings API endpoint](/server/rest-api/corpus-management/sharing-save.html) for details.

The sharing information is stored in the corpus directory in a file named `.shareWithUsers`.

