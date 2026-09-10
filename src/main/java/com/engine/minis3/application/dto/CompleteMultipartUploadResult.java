package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "CompleteMultipartUploadResult")
public class CompleteMultipartUploadResult {

    @JacksonXmlProperty(localName = "Location")
    private String location;

    @JacksonXmlProperty(localName = "Bucket")
    private String bucket;

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "ETag")
    private String eTag;

    public CompleteMultipartUploadResult() {}

    public CompleteMultipartUploadResult(String location, String bucket, String key, String eTag) {
        this.location = location;
        this.bucket = bucket;
        this.key = key;
        this.eTag = "\"" + eTag.replace("\"", "") + "\"";
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
    }
}
