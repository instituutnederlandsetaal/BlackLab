package nl.inl.blacklab.server.datastream;

public enum DataFormat {
    XML,
    JSON,
    CSV;

    /**
     * Returns the desired content type for the output. This is based on the
     * "outputformat" parameter.
     *
     * @return the MIME content type
     */
    public String getContentType() {
        return switch (this) {
            case XML -> "application/xml";
            case CSV -> "text/csv";
            default -> "application/json";
        };
    }

    /**
     * Translate the string value for outputType to the enum OutputType value.
     *
     * @param typeString the outputType string
     * @param defaultValue what to use if neither matches
     * @return the OutputType enum value
     */
    public static DataFormat fromString(String typeString, DataFormat defaultValue) {
        if (typeString.equalsIgnoreCase("xml"))
            return DataFormat.XML;
        if (typeString.equalsIgnoreCase("json"))
            return DataFormat.JSON;
        if (typeString.equalsIgnoreCase("csv"))
            return DataFormat.CSV;
        return defaultValue;
    }
}
