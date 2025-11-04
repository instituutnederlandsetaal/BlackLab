import nl.inl.blacklab.exceptions.PluginException
import nl.inl.blacklab.index.IndexSource
import nl.inl.blacklab.plugins.IndexSourceType
import nl.inl.util.Json
import nl.inl.util.fileprocessor.FileIterator
import nl.inl.util.fileprocessor.FileIteratorAbstract
import nl.inl.util.fileprocessor.FileReference

/** Duct API URL if not overridden in config */
String DEFAULT_DUCT_API_URL = "http://duct.ivdnt.loc/duct/api"

return new IndexSourceType() {

    String ductApiUrl

    @Override
    void initialize() throws PluginException {
        super.initialize()
        ductApiUrl = cfgString("ductApiUrl", DEFAULT_DUCT_API_URL)
    }

    @Override
    IndexSource get(String path) {

        def jsonMapper = Json.getJsonObjectMapper();

        return new IndexSource(path) {
            @Override
            FileIterator filesToIndex() {
                // TODO: paging (in Duct API and here)
                def fileIds = findFiles(path)

                // Return an iterator over the files found
                return new FileIteratorAbstract(getFileIteratorSettings()) {
                    def it = fileIds.iterator()

                    @Override
                    void close() {
                        // nothing to do
                    }

                    @Override
                    boolean hasNext() {
                        return it.hasNext()
                    }

                    @Override
                    FileReference next() {
                        return getFileFromDuct(it.next())
                    }
                }
            }

            private List<String> findFiles(String metadataQuery) {
                // Build URL
                def searchUrl = new URL(ductApiUrl + "/file/search?path=&state-spec=verrijkt&meta-data-query=" +
                        URLEncoder.encode(metadataQuery, "UTF-8") +
                        "&recursive=true&include-directories=false&search=true&max-files=100&timeout=300")
                // Find files
                def connection = searchUrl.openConnection()
                connection.setRequestProperty("Accept", "application/json")
                List<String> fileIds
                try (def inputStream = connection.getInputStream()) {
                    // Read JSON response
                    def response = jsonMapper.readValue(inputStream, Map.class)
                    def files = response['files']
                    if (files == null || files.isEmpty()) {
                        throw new IllegalArgumentException("No files found in Duct for path: " + path)
                    }
                    // Extract file IDs
                    fileIds = files.collect { it['id'] as String }
                }
                return fileIds
            }

            /** Get a single file from Duct by its ID. */
            FileReference getFileFromDuct(String id) {
                def url = new URL(ductApiUrl + "/file/" + id)
                def connection = url.openConnection()
                connection.setRequestProperty("Accept", "application/json")
                try (def inputStream = connection.getInputStream()) {
                    // Read JSON response
                    def response = jsonMapper.readValue(inputStream, Map.class)
                    String filePath = response['file']['info']['path']
                    String content = response['content']
                    return FileReference.fromCharArray(filePath, content.toCharArray(), null)
                }
            }
        }
    }
}
