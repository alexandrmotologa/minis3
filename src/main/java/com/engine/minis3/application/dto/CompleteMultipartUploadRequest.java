package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "CompleteMultipartUpload")
public class CompleteMultipartUploadRequest {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Part")
    private List<PartRequestDto> parts;

    public CompleteMultipartUploadRequest() {}

    public CompleteMultipartUploadRequest(List<PartRequestDto> parts) {
        this.parts = parts;
    }

    public List<PartRequestDto> getParts() {
        return parts;
    }

    public void setParts(List<PartRequestDto> parts) {
        this.parts = parts;
    }

    public static class PartRequestDto {
        @JacksonXmlProperty(localName = "PartNumber")
        private int partNumber;

        @JacksonXmlProperty(localName = "ETag")
        private String eTag;

        public PartRequestDto() {}

        public PartRequestDto(int partNumber, String eTag) {
            this.partNumber = partNumber;
            this.eTag = eTag;
        }

        public int getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(int partNumber) {
            this.partNumber = partNumber;
        }

        public String getETag() {
            return eTag;
        }

        public void setETag(String eTag) {
            this.eTag = eTag;
        }
    }
}
