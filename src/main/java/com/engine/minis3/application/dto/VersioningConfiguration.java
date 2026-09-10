package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "VersioningConfiguration", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class VersioningConfiguration {

    @JacksonXmlProperty(localName = "Status")
    private String status;

    public VersioningConfiguration() {
    }

    public VersioningConfiguration(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
