package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.ApiCredential;

import java.util.List;
import java.util.Optional;

/**
 * Port for managing API credentials and IAM authentication policies.
 */
public interface CredentialRepositoryPort {
    void saveCredential(ApiCredential credential);
    Optional<ApiCredential> findByAccessKey(String accessKey);
    List<ApiCredential> listCredentials();
    void deleteCredential(String accessKey);
}
