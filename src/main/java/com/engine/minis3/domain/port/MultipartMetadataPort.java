package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.MultipartPart;
import com.engine.minis3.domain.model.MultipartUpload;

import java.util.List;
import java.util.Optional;

public interface MultipartMetadataPort {
    void saveUpload(MultipartUpload upload);
    Optional<MultipartUpload> findUpload(String uploadId);
    void deleteUpload(String uploadId);
    void savePart(MultipartPart part);
    List<MultipartPart> listParts(String uploadId);
    Optional<MultipartPart> findPart(String uploadId, int partNumber);
}
