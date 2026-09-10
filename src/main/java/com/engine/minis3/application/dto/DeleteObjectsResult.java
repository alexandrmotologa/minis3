package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "DeleteResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class DeleteObjectsResult {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Deleted")
    private List<DeletedObject> deleted = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Error")
    private List<DeleteError> errors = new ArrayList<>();

    public DeleteObjectsResult() {
    }

    public List<DeletedObject> getDeleted() {
        return deleted;
    }

    public void setDeleted(List<DeletedObject> deleted) {
        this.deleted = deleted;
    }

    public List<DeleteError> getErrors() {
        return errors;
    }

    public void setErrors(List<DeleteError> errors) {
        this.errors = errors;
    }

    public static class DeletedObject {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        @JacksonXmlProperty(localName = "DeleteMarker")
        private Boolean deleteMarker;

        public DeletedObject() {
        }

        public DeletedObject(String key, String versionId, Boolean deleteMarker) {
            this.key = key;
            this.versionId = (versionId != null && !"null".equalsIgnoreCase(versionId)) ? versionId : null;
            this.deleteMarker = deleteMarker;
        }

        public String getKey() {
            return key;
        }

        public String getVersionId() {
            return versionId;
        }

        public Boolean getDeleteMarker() {
            return deleteMarker;
        }
    }

    public static class DeleteError {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "Code")
        private String code;

        @JacksonXmlProperty(localName = "Message")
        private String message;

        public DeleteError() {
        }

        public DeleteError(String key, String code, String message) {
            this.key = key;
            this.code = code;
            this.message = message;
        }

        public String getKey() {
            return key;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
