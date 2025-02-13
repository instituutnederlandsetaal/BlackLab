# Tutorials / howtos

## Let users manage their own corpora

If you configure a form of user authentication, you can allow users to manager their own corpora using BlackLab Server or Frontend.

BlackLab Server includes support for creating indices and adding documents to them. We use these features in BlackLab Frontend to allow users to quickly index data and search it, without having to set up a BlackLab installation themselves. Here's a very quick overview.

Currently, only private indices can be created and appended to. This means there must be a logged-in user. The `authentication` section in `blacklab-server.yaml` will let you specify what authentication system you'd like to use. If you specify class `AuthDebugFixed` and a `userId`, you will always be logged in as this user. Have a look at the other `Auth*` classes (mostly `AuthHttpBasic` and `AuthRequestAttribute`) to see how real authentication would work.

Another required setting is `userIndexes` (in addition to `indexLocations` which points to the "globally available" indices). In this directory, user-private indices will be created. Obviously, the application needs write permissions on this directory.

When a user is logged in and you have a `userIndexes` directory set up, you will see a `user` section on the BlackLab Server info page (`/blacklab-server/`) with both `loggedIn` and `canCreateIndex` set to `true`. To see what input formats are supported, look at the `/blacklab-server/input-formats/` URL.

To create a private index, `POST` to `/blacklab-server/` with parameters `name` (index identifier), `display` (a human-friendly index name) and `format` (the input format to use for this index, e.g. `tei`). The userId will be prepended to the index name, so if your userId is `myUserId` and you create an index name `myIndex`, the full name will be `myUserId:myIndex`.

To add a file to a private index, upload it to `/blacklab-server/INDEX_NAME/docs` with parameter name `data`.

To remove a private index, send a `DELETE` request to `/blacklab-server/INDEX_NAME/`.

For the details of these endpoints, and the ones below, see [Manage user corpora](rest-api/README.md#manage-user-corpora).

### Adding/removing user formats

To add an input format, upload a `.yaml` or `.json` configuration file to the `/blacklab-server/input-formats/` URL with parameter name `data`. The file name will become the format name. User formats will be prefixed with the `userId` and a colon, so if your userId is `myUserId` and you upload a file `myFormatName.blf.yaml`, a new format `myUserId:myFormatName` will be created. Only you will see it in the formats list, but in theory, everyone can use it (this is different from indices, which are private).

To view an input format configuration, use `/blacklab-server/input-formats/<format-name>`.

To remove an input format, send a `DELETE` request to the format page, e.g. `/blacklab-server/input-formats/<format-name>`.

### Share private index with a list of users

To see what users (if any) a private index is currently shared with, use: `/blacklab-server/<corpus-name>/sharing`.

To set the list of users to share a private index with, send a `POST` request to the same URL with the `users[]` parameter for each user to share with (that is, you should specify this parameter multiple times, once for each user). You can leave the parameter empty if you don't want to share the index anymore.

The sharing information is stored in the index directory in a file named `.shareWithUsers`.


## Lock files into disk cache

::: warning CAUTION
Depending on your requirements and hardware, this may be a bad idea that degrades performance. In general, most users should rely probably on the operating system to effectively cache files, and not try to override it using a tool like `vmtouch`. This information is still provided in case anyone wants to use `vmtouch` nonetheless.
:::

At the Dutch Language Institute, we used to use a tool called [vmtouch](http://hoytech.com/vmtouch/) ([GitHub](https://github.com/hoytech/vmtouch)) written by Doug Hoyte to 'lock' our forward indices in the operating system's disk cache, keeping them in memory at all times. This speeds up sorting and grouping operations, as well as generating (large amounts of) KWICs (keyword-in-context results).

vmtouch is a tool that can "lock" a file in disk cache. It benefits applications that need to perform fast random access to large files (i.e. several gigabytes). Corpus search applications fall into this domain: they need random access to the "forward index" component of the index to do fast sorting and grouping.

You should be careful to ensure the machine you're using has enough RAM to keep the required files in memory permanently, and will still have memory left over for the operating system and applications.

Also important is to run vmtouch as the root user; user accounts have a limit to the amount of memory they may lock. Vmtouch will terminate with an out of memory error if it hits that limit. (it may be possible to raise this limit for a user by changing a configuration file - we haven't experimented with this)

The [official page for vmtouch](http://hoytech.com/vmtouch/) has the C source code and the online manual. We've made a slight modification to the source code to allow for larger files to be cached.


### Filesize limit

The original vmtouch had a built-in file size limit (probably as a safety precaution). You may need to raise it (search for o_max_file_size).


### Running vmtouch

To run vmtouch in daemon mode, so that it will lock files in the disk cache, use the following command line:

	sudo vmtouch -vtld <list_of_files>

The switches: v=verbose, t=touch (load into disk cache), l=lock (lock in disk cache), d=daemon (keep the program running). For example, we use the following command line to keep all four forward indices of our BlackLab index locked in disk cache (run from within the index directory):

	sudo vmtouch -vtld fi_contents%word/tokens.dat fi_contents%lemma/tokens.dat fi_contents%pos/tokens.dat fi_contents%punct/tokens.dat

The daemon will start up and will take a while to load all files into disk cache. You can check its progress by only specifying the -v option:

	sudo vmtouch -v fi_contents%word/tokens.dat fi_contents%lemma/tokens.dat fi_contents%pos/tokens.dat fi_contents%punct/tokens.dat

