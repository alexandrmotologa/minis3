package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
public class ListAllMyBucketsResult {

    @JacksonXmlProperty(localName = "Owner")
    private OwnerDto owner = new OwnerDto();

    @JacksonXmlElementWrapper(localName = "Buckets")
    @JacksonXmlProperty(localName = "Bucket")
    private List<BucketDto> buckets;

    public ListAllMyBucketsResult() {}

    public ListAllMyBucketsResult(List<BucketDto> buckets) {
        this.buckets = buckets;
    }

    public OwnerDto getOwner() {
        return owner;
    }

    public void setOwner(OwnerDto owner) {
        this.owner = owner;
    }

    public List<BucketDto> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<BucketDto> buckets) {
        this.buckets = buckets;
    }

    public static class OwnerDto {
        @JacksonXmlProperty(localName = "ID")
        private String id = "minis3-owner-id";

        @JacksonXmlProperty(localName = "DisplayName")
        private String displayName = "minis3-admin";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class BucketDto {
        @JacksonXmlProperty(localName = "Name")
        private String name;

        @JacksonXmlProperty(localName = "CreationDate")
        private String creationDate;

        public BucketDto() {}

        public BucketDto(String name, String creationDate) {
            this.name = name;
            this.creationDate = creationDate;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(String creationDate) {
            this.creationDate = creationDate;
        }
    }
}
