# Manage user corpora

If [user authentication and private user corpora](/server/howtos.md#let-users-manage-their-own-corpora) are enabled, these can be used to manage the user's own corpora: creating/deleting, adding data and sharing.

("global" corpora, that don't belong to a specific user, can not be managed through these endpoints at this time)

* [Create corpus](create.md) : `POST /blacklab-server`<br>Create a new corpus.
* [Delete corpus](delete.md) : `DELETE /blacklab-server/<corpus-name>`<br>Delete the specified corpus.
* [Add documents to corpus](add-documents.md) : `POST /blacklab-server/<corpus-name>/docs`<br>Index the uploaded input files, adding documents to the corpus.
* [Get corpus sharing settings](sharing-get.md) : `GET /blacklab-server/<corpus-name>/sharing`<br>Return the list of users with whom the corpus is shared, if any.
* [Update corpus sharing settings](sharing-save.md) : `POST /blacklab-server/<corpus-name>/sharing`<br>Set or update the list of users to share the corpus with.
* [Corpora shared with me](shared-with-me.md) : `GET /blacklab-server/shared-with-me`<br>Return a list of corpora shared with the currently logged-in user.

