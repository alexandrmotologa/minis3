package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "ListBucketResult")
public class ListBucketResult {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Prefix")
    private String prefix;

    @JacksonXmlProperty(localName = "KeyCount")
    private int keyCount;

    @JacksonXmlProperty(localName = "MaxKeys")
    private int maxKeys;

    @JacksonXmlProperty(localName = "IsTruncated")
    private boolean isTruncated;

    @JacksonXmlProperty(localName = "NextContinuationToken")
    private String nextContinuationToken;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Contents")
    private List<S3ObjectSummaryDto> contents;

    public ListBucketResult() {}

    public ListBucketResult(String name, String prefix, int keyCount, int maxKeys,
                            boolean isTruncated, String nextContinuationToken,
                            List<S3ObjectSummaryDto> contents) {
        this.name = name;
        this.prefix = prefix;
        this.keyCount = keyCount;
        this.maxKeys = maxKeys;
        this.isTruncated = isTruncated;
        this.nextContinuationToken = nextContinuationToken;
        this.contents = contents;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getKeyCount() {
        return keyCount;
    }

    public void setKeyCount(int keyCount) {
        this.keyCount = keyCount;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    public boolean isTruncated() {
        return isTruncated;
    }

    public void setTruncated(boolean truncated) {
        isTruncated = truncated;
    }

    public String getNextContinuationToken() {
        return nextContinuationToken;
    }

    public void setNextContinuationToken(String nextContinuationToken) {
        this.nextContinuationToken = nextContinuationToken;
    }

    public List<S3ObjectSummaryDto> getContents() {
        return contents;
    }

    public void setContents(List<S3ObjectSummaryDto> contents) {
        this.contents = contents;
    }

    public static class S3ObjectSummaryDto {
        @JacksonXmlProperty(localName = "Key")
        private String key;

        @JacksonXmlProperty(localName = "LastModified")
        private String lastModified;

        @JacksonXmlProperty(localName = "ETag")
        private String eTag;

        @JacksonXmlProperty(localName = "Size")
        private long size;

        @JacksonXmlProperty(localName = "StorageClass")
        private String storageClass = "STANDARD";

        public S3ObjectSummaryDto() {}

        public S3ObjectSummaryDto(String key, String lastModified, String eTag, long size) {
            this.key = key;
            this.lastModified = lastModified;
            this.eTag = "\"" + eTag.replace("\"", "") + "\"";
            this.size = size;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLastModified() {
            return lastModified;
        }

        public void setLastModified(String lastModified) {
            this.lastModified = lastModified;
        }

        public String getETag() {
            return eTag;
        }

        public void setETag(String eTag) {
            this.eTag = eTag;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getStorageClass() {
            return storageClass;
        }

        public void setStorageClass(String storageClass) {
            this.storageClass = storageClass;
        }
    }
}
