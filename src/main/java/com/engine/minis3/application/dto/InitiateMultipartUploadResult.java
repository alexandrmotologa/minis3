package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "InitiateMultipartUploadResult")
public class InitiateMultipartUploadResult {

    @JacksonXmlProperty(localName = "Bucket")
    private String bucket;

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "UploadId")
    private String uploadId;

    public InitiateMultipartUploadResult() {}

    public InitiateMultipartUploadResult(String bucket, String key, String uploadId) {
        this.bucket = bucket;
        this.key = key;
        this.uploadId = uploadId;
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
}
