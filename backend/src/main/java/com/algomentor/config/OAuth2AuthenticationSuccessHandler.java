package com.algomentor.config;

import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OAuth2Properties oAuth2Properties;

    public OAuth2AuthenticationSuccessHandler(
            UserRepository userRepository,
            JwtService jwtService,
            OAuth2Properties oAuth2Properties) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.oAuth2Properties = oAuth2Properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not found in Google OAuth response");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name != null && !name.isBlank() ? name : email);
            newUser.setRole(Role.USER);
            newUser.setAuthProvider("GOOGLE");
            return userRepository.save(newUser);
        });

        String jwt = jwtService.generate(user);
        String targetUrl = oAuth2Properties.getRedirectUri() + "#token=" + jwt;

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
