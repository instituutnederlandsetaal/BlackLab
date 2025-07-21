package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="blacklabResponse")
public class ErrorResponse {

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Desc {
        int httpStatusCode = 200;

        String code;

        String message;

        String stackTrace;

        private Desc() {}

        public Desc(int httpStatusCode, String code, String message, String stackTrace) {
            this.code = code;
            this.message = message;
            this.stackTrace = stackTrace;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getStackTrace() {
            return message;
        }
    }

    Desc error;

    private ErrorResponse() {}

    public ErrorResponse(Desc error) {
        this.error = error;
    }

    public ErrorResponse(int httpStatusCode, String code, String message, String stackTrace) {
        this.error = new Desc(httpStatusCode, code, message, stackTrace);
    }

    public String getMessage() {
        return error.code + "||" + error.message;
    }

    public void setError(int httpStatusCode, String code, String message, String stackTrace) {
        error = new Desc(httpStatusCode, code, message, stackTrace);
    }

    public Desc getError() {
        return error;
    }
}
