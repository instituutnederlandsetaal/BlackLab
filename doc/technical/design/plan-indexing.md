# Refactor BlackLab's indexing code

Over the years, BlackLab's indexing code has become messy. We'd like to clean it up and refactor it and make it easier to improve and maintain, and especially make it easier for users to implement their own `DocIndexer`s (or equivalent) if needed.

The first step would be to get rid of the old external index format, which will allow us to delete a lot of code. This will probably be done when we start developing 5.x, at least if we make the integrated index format the default in 4.0. (BlackLab Frontend needs to use the `/relations` endpoint to enable this)

After that, we can start refactoring the indexing code:
- probably most important is a cleaner API for configuring a new index, i.e. the fields with their settings. Currently this is quite messy, requiring several manual steps to do some things. This should be abstracted away so the code just reads like an index structure definition.
- We should try to improve multi-threaded processing. Currently you can only limit how many files are being processed at once, but if some files are huge and others are tiny, this can either exhaust the available memory by just processing 2 huge files or not use much of it at all by processing only 5 tiny files at once. Maybe it should be possible to set a limit on the sum of document sizes we're processing at once.
- We should think about `DocIndexer` and how to make it easier to implement your own, aside from what's already mentioned. Base classes should be provided that take care of most of the heavy lifting.
- It might be nice if we can process zip/tar.gz files in parallel. E.g. extract several files, then process them in parallel. This would likely extracting files to a separate buffer, then queueing them for indexing.
