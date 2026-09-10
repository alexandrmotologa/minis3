package com.engine.minis3.application.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.UUID;

@JacksonXmlRootElement(localName = "Error")
public class ErrorResponse {

    @JacksonXmlProperty(localName = "Code")
    private String code;

    @JacksonXmlProperty(localName = "Message")
    private String message;

    @JacksonXmlProperty(localName = "Resource")
    private String resource;

    @JacksonXmlProperty(localName = "RequestId")
    private String requestId;

    public ErrorResponse() {
        this.requestId = UUID.randomUUID().toString();
    }

    public ErrorResponse(String code, String message, String resource) {
        this.code = code;
        this.message = message;
        this.resource = resource;
        this.requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
