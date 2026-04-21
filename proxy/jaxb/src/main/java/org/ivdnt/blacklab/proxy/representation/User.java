package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class User {

    public boolean loggedIn = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String id;

    public boolean canCreateIndex = false;

    public boolean debugMode = false;

    @SuppressWarnings("unused")
    public User() {
        // no-arg ctor required by Jersey
    }

    @Override
    public String toString() {
        return "User{" +
                "loggedIn=" + loggedIn +
                ", id='" + id + '\'' +
                ", canCreateIndex=" + canCreateIndex +
                ", debugMode=" + debugMode +
                '}';
    }
}
