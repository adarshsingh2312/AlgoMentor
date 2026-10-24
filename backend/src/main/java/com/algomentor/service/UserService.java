package com.algomentor.service;

import com.algomentor.domain.User;
import com.algomentor.dto.UserResponseDto;
import com.algomentor.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final CodeforcesService codeforcesService;

    public UserService(UserRepository userRepository, CodeforcesService codeforcesService) {
        this.userRepository = userRepository;
        this.codeforcesService = codeforcesService;
    }

    @Transactional(readOnly = true)
    public UserResponseDto getProfile(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return UserResponseDto.from(user);
    }

    public UserResponseDto updateCfHandle(UUID userId, String handle) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify handle exists on Codeforces
        codeforcesService.verifyHandleExists(handle);

        user.setCfHandle(handle);
        User updated = userRepository.save(user);

        return UserResponseDto.from(updated);
    }
}
