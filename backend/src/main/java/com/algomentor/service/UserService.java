package com.algomentor.service;

import com.algomentor.domain.User;
import com.algomentor.dto.UserResponseDto;
import com.algomentor.repository.NoteDocumentRepository;
import com.algomentor.repository.RecommendationRepository;
import com.algomentor.repository.SubmissionRepository;
import com.algomentor.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final RecommendationRepository recommendationRepository;
    private final NoteDocumentRepository noteDocumentRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       SubmissionRepository submissionRepository,
                       RecommendationRepository recommendationRepository,
                       NoteDocumentRepository noteDocumentRepository) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.recommendationRepository = recommendationRepository;
        this.noteDocumentRepository = noteDocumentRepository;
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

    @Transactional
    public User updateCfHandle(UUID userId, String handle, Integer cfRating, String cfRank) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String oldHandle = user.getCfHandle();
        boolean isChange = oldHandle != null && !oldHandle.trim().equalsIgnoreCase(handle.trim());

        if (isChange) {
            clearUserData(userId);
            log.info("[User] Cleared previous Codeforces data for user {} due to handle switch from {} to {}",
                    userId, oldHandle, handle);
        }

        user.setCfHandle(handle.trim());
        user.setCfRating(cfRating);
        user.setCfRank(cfRank);
        return userRepository.save(user);
    }

    @Transactional
    public void updateCfRating(UUID userId, Integer cfRating, String cfRank) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(user -> {
            user.setCfRating(cfRating);
            user.setCfRank(cfRank);
            userRepository.save(user);
        });
    }

    @Transactional
    public UserResponseDto disconnectCfHandle(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        clearUserData(userId);
        user.setCfHandle(null);
        user.setCfRating(null);
        user.setCfRank(null);
        user.setLastDigestDate(null);
        User updated = userRepository.save(user);

        log.info("[User] Disconnected Codeforces handle for user {}", userId);
        return UserResponseDto.from(updated);
    }

    private void clearUserData(UUID userId) {
        if (submissionRepository != null) {
            submissionRepository.deleteByUserId(userId);
        }
        if (recommendationRepository != null) {
            recommendationRepository.deleteByUserId(userId);
        }
        if (noteDocumentRepository != null) {
            noteDocumentRepository.deleteAutoStruggleNotesByUserId(userId);
        }
    }
}
