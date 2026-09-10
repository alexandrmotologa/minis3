package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "Delete", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class DeleteObjectsRequest {

    @JacksonXmlProperty(localName = "Quiet")
    private Boolean quiet = false;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Object")
    private List<ObjectIdentifier> objects = new ArrayList<>();

    public DeleteObjectsRequest() {
    }

    public Boolean getQuiet() {
        return quiet != null && quiet;
    }

    public void setQuiet(Boolean quiet) {
        this.quiet = quiet;
    }

    public List<ObjectIdentifier> getObjects() {
        return objects;
    }

    public void setObjects(List<ObjectIdentifier> objects) {
        this.objects = objects;
    }

    public static class ObjectIdentifier {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        public ObjectIdentifier() {
        }

        public ObjectIdentifier(String key, String versionId) {
            this.key = key;
            this.versionId = versionId;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }
    }
}
