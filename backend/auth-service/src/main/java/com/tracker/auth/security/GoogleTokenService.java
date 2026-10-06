package com.tracker.auth.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.tracker.auth.models.User;
import com.tracker.auth.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GoogleTokenService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    private final UserRepository userRepository;
    private final EncryptionUtils encryptionUtils;

    public GoogleTokenService(UserRepository userRepository, EncryptionUtils encryptionUtils) {
        this.userRepository = userRepository;
        this.encryptionUtils = encryptionUtils;
    }

    public String getShortLivedAccessToken(Long userId) throws Exception {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = userOpt.get();
        if (user.getGoogleRefreshToken() == null) {
            throw new RuntimeException("No Google Refresh Token found for user");
        }

        String decryptedRefreshToken = encryptionUtils.decrypt(user.getGoogleRefreshToken());

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(decryptedRefreshToken);

        credential.refreshToken();
        return credential.getAccessToken();
    }
}
