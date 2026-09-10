package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "ListPartsResult")
public class ListPartsResult {

    @JacksonXmlProperty(localName = "Bucket")
    private String bucket;

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "UploadId")
    private String uploadId;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Part")
    private List<PartSummaryDto> parts;

    public ListPartsResult() {}

    public ListPartsResult(String bucket, String key, String uploadId, List<PartSummaryDto> parts) {
        this.bucket = bucket;
        this.key = key;
        this.uploadId = uploadId;
        this.parts = parts;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public List<PartSummaryDto> getParts() {
        return parts;
    }

    public void setParts(List<PartSummaryDto> parts) {
        this.parts = parts;
    }

    public static class PartSummaryDto {
        @JacksonXmlProperty(localName = "PartNumber")
        private int partNumber;

        @JacksonXmlProperty(localName = "LastModified")
        private String lastModified;

        @JacksonXmlProperty(localName = "ETag")
        private String eTag;

        @JacksonXmlProperty(localName = "Size")
        private long size;

        public PartSummaryDto() {}

        public PartSummaryDto(int partNumber, String lastModified, String eTag, long size) {
            this.partNumber = partNumber;
            this.lastModified = lastModified;
            this.eTag = "\"" + eTag.replace("\"", "") + "\"";
            this.size = size;
        }

        public int getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(int partNumber) {
            this.partNumber = partNumber;
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
    }
}
