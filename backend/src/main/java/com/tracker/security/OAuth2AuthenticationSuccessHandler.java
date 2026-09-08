package com.tracker.security;

import com.tracker.models.User;
import com.tracker.repositories.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final EncryptionUtils encryptionUtils;

    public OAuth2AuthenticationSuccessHandler(JwtUtils jwtUtils, UserRepository userRepository,
                                              OAuth2AuthorizedClientService authorizedClientService,
                                              EncryptionUtils encryptionUtils) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.authorizedClientService = authorizedClientService;
        this.encryptionUtils = encryptionUtils;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        
        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");
        
        // Find or create user
        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
        } else {
            user = new User();
            user.setEmail(email);
            // Default username to email prefix if not exists
            user.setUsername(email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 4));
            user.setPassword(""); // OAuth users don't need passwords
        }
        
        user.setGoogleId(googleId);
        user.setGoogleConnected(true);

        // Capture Refresh Token
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        
        if (client != null && client.getRefreshToken() != null) {
            String refreshToken = client.getRefreshToken().getTokenValue();
            user.setGoogleRefreshToken(encryptionUtils.encrypt(refreshToken));
        }

        userRepository.save(user);

        // Generate JWT
        String jwt = jwtUtils.generateTokenFromUsername(user.getUsername());
        
        // Redirect to Frontend
        String targetUrl = "http://localhost:4200/oauth2/redirect?token=" + jwt;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
