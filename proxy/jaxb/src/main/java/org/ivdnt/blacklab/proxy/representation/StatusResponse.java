package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.ivdnt.blacklab.proxy.representation.ErrorResponse.Desc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="blacklabResponse")
public class StatusResponse {

    Desc status;

    /** Which node returned the status? */
    @JsonInclude(Include.NON_EMPTY)
    String nodeUrl = "";

    private StatusResponse() {}

    public StatusResponse(Desc status) {
        this.status = status;
    }

    public StatusResponse(String code, String message, String stackTrace) {
        this.status = new Desc(200, code, message, stackTrace);
    }

    public String getMessage() {
        return status.code + "||" + status.message;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

    public void setStatus(String code, String message, String stackTrace) {
        status = new Desc(200, code, message, stackTrace);
    }

    public Desc getStatus() {
        return status;
    }
}
