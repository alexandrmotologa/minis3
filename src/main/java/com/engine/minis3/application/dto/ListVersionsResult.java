package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "ListVersionsResult", namespace = "http://s3.amazonaws.com/doc/2006-03-01/")
public class ListVersionsResult {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Prefix")
    private String prefix;

    @JacksonXmlProperty(localName = "KeyMarker")
    private String keyMarker;

    @JacksonXmlProperty(localName = "VersionIdMarker")
    private String versionIdMarker;

    @JacksonXmlProperty(localName = "MaxKeys")
    private int maxKeys;

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean isTruncated;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Version")
    private List<VersionEntry> versions = new ArrayList<>();

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "DeleteMarker")
    private List<DeleteMarkerEntry> deleteMarkers = new ArrayList<>();

    public ListVersionsResult() {
    }

    public ListVersionsResult(String name, String prefix, String keyMarker, String versionIdMarker, int maxKeys, boolean isTruncated) {
        this.name = name;
        this.prefix = (prefix != null) ? prefix : "";
        this.keyMarker = (keyMarker != null) ? keyMarker : "";
        this.versionIdMarker = (versionIdMarker != null) ? versionIdMarker : "";
        this.maxKeys = maxKeys;
        this.isTruncated = isTruncated;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getKeyMarker() {
        return keyMarker;
    }

    public String getVersionIdMarker() {
        return versionIdMarker;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public boolean isTruncated() {
        return isTruncated;
    }

    public List<VersionEntry> getVersions() {
        return versions;
    }

    public void setVersions(List<VersionEntry> versions) {
        this.versions = versions;
    }

    public List<DeleteMarkerEntry> getDeleteMarkers() {
        return deleteMarkers;
    }

    public void setDeleteMarkers(List<DeleteMarkerEntry> deleteMarkers) {
        this.deleteMarkers = deleteMarkers;
    }

    public static class VersionEntry {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        @JacksonXmlProperty(localName = "IsLatest")
        private boolean isLatest;

        @JacksonXmlProperty(localName = "LastModified")
        private String lastModified;

        @JacksonXmlProperty(localName = "ETag")
        private String etag;

        @JacksonXmlProperty(localName = "Size")
        private long size;

        @JacksonXmlProperty(localName = "StorageClass")
        private String storageClass = "STANDARD";

        public VersionEntry() {
        }

        public VersionEntry(String key, String versionId, boolean isLatest, String lastModified, String etag, long size) {
            this.key = key;
            this.versionId = versionId;
            this.isLatest = isLatest;
            this.lastModified = lastModified;
            this.etag = etag;
            this.size = size;
        }

        public String getKey() {
            return key;
        }

        public String getVersionId() {
            return versionId;
        }

        public boolean isLatest() {
            return isLatest;
        }

        public String getLastModified() {
            return lastModified;
        }

        public String getEtag() {
            return etag;
        }

        public long getSize() {
            return size;
        }

        public String getStorageClass() {
            return storageClass;
        }
    }

    public static class DeleteMarkerEntry {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "VersionId")
        private String versionId;

        @JacksonXmlProperty(localName = "IsLatest")
        private boolean isLatest;

        @JacksonXmlProperty(localName = "LastModified")
        private String lastModified;

        public DeleteMarkerEntry() {
        }

        public DeleteMarkerEntry(String key, String versionId, boolean isLatest, String lastModified) {
            this.key = key;
            this.versionId = versionId;
            this.isLatest = isLatest;
            this.lastModified = lastModified;
        }

        public String getKey() {
            return key;
        }

        public String getVersionId() {
            return versionId;
        }

        public boolean isLatest() {
            return isLatest;
        }

        public String getLastModified() {
            return lastModified;
        }
    }
}
