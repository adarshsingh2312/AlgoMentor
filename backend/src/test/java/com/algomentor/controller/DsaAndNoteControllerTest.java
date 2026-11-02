package com.algomentor.controller;

import com.algomentor.config.DateTimeConfig;
import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.DifficultyLevel;
import com.algomentor.domain.NoteStatus;
import com.algomentor.domain.Role;
import com.algomentor.domain.TopicStatus;
import com.algomentor.domain.User;
import com.algomentor.dto.DsaRoadmapDto;
import com.algomentor.dto.DsaSectionDto;
import com.algomentor.dto.DsaTopicProgressDto;
import com.algomentor.dto.NoteDocumentDetailDto;
import com.algomentor.dto.NoteUploadResult;
import com.algomentor.dto.UpdateProgressRequest;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.DsaService;
import com.algomentor.service.JwtService;
import com.algomentor.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DsaController.class, NoteController.class})
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class,
        GlobalExceptionHandler.class,
        DateTimeConfig.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback",
        "app.timezone=UTC"
})
class DsaAndNoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DsaService dsaService;
    @MockBean private NoteService noteService;
    @MockBean private UserRepository userRepository;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("coder@example.com");
        testUser.setName("Coder");
        testUser.setRole(Role.USER);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

        token = "Bearer " + jwtService.generate(testUser);
    }

    @Test
    @DisplayName("GET /api/me/dsa returns 200 with roadmap structure")
    void getRoadmapReturns200() throws Exception {
        DsaTopicProgressDto item = new DsaTopicProgressDto(
                UUID.randomUUID(), "Two Sum", "https://leetcode.com/problems/two-sum/",
                DifficultyLevel.EASY, TopicStatus.TODO
        );
        DsaSectionDto section = new DsaSectionDto("Arrays", List.of(item), 0, 1);
        DsaRoadmapDto roadmap = new DsaRoadmapDto(List.of(section), 0, 1);

        when(dsaService.getRoadmap(testUser.getId())).thenReturn(roadmap);

        mockMvc.perform(get("/api/me/dsa")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTopics").value(1))
                .andExpect(jsonPath("$.totalDone").value(0))
                .andExpect(jsonPath("$.sections[0].section").value("Arrays"))
                .andExpect(jsonPath("$.sections[0].items[0].title").value("Two Sum"));
    }

    @Test
    @DisplayName("GET /api/me/dsa without token returns 401 Unauthorized")
    void getRoadmapWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/me/dsa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/me/dsa/{topicId}/progress updates progress and returns 204")
    void updateProgressReturns204() throws Exception {
        UUID topicId = UUID.randomUUID();
        UpdateProgressRequest request = new UpdateProgressRequest(TopicStatus.DONE);

        mockMvc.perform(put("/api/me/dsa/" + topicId + "/progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(dsaService).updateProgress(testUser.getId(), topicId, TopicStatus.DONE);
    }

    @Test
    @DisplayName("PUT /api/me/dsa/{topicId}/progress for unknown topic returns 404")
    void updateProgressUnknownTopicReturns404() throws Exception {
        UUID topicId = UUID.randomUUID();
        UpdateProgressRequest request = new UpdateProgressRequest(TopicStatus.DONE);

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found"))
                .when(dsaService).updateProgress(eq(testUser.getId()), eq(topicId), eq(TopicStatus.DONE));

        mockMvc.perform(put("/api/me/dsa/" + topicId + "/progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/me/notes upload valid .md returns 201 Created")
    void uploadValidMarkdownReturns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dsa.md", "text/markdown", "# Notes".getBytes()
        );

        NoteDocumentDetailDto dto = new NoteDocumentDetailDto(
                UUID.randomUUID(), "dsa.md", "text/markdown", 7L,
                "somehash", "# Notes", NoteStatus.UPLOADED, LocalDateTime.now()
        );

        when(noteService.upload(eq(testUser.getId()), any()))
                .thenReturn(new NoteUploadResult(dto, false));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("dsa.md"))
                .andExpect(jsonPath("$.content").value("# Notes"));
    }

    @Test
    @DisplayName("POST /api/me/notes duplicate upload returns 200 OK with existing note")
    void uploadDuplicateReturns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dsa.md", "text/markdown", "# Notes".getBytes()
        );

        NoteDocumentDetailDto dto = new NoteDocumentDetailDto(
                UUID.randomUUID(), "existing.md", "text/markdown", 7L,
                "somehash", "# Notes", NoteStatus.UPLOADED, LocalDateTime.now()
        );

        when(noteService.upload(eq(testUser.getId()), any()))
                .thenReturn(new NoteUploadResult(dto, true));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("existing.md"));
    }

    @Test
    @DisplayName("POST /api/me/notes with unsupported file (.exe) returns 400 Bad Request")
    void uploadInvalidExtensionReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "danger.exe", "application/octet-stream", "evil".getBytes()
        );

        when(noteService.upload(eq(testUser.getId()), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .md and .txt files are allowed"));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only .md and .txt files are allowed"));
    }

    @Test
    @DisplayName("POST /api/me/notes with empty file returns 400 Bad Request")
    void uploadEmptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        when(noteService.upload(eq(testUser.getId()), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty"));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is empty"));
    }

    @Test
    @DisplayName("GET /api/me/notes/{id} for another user's note returns 404 Not Found")
    void getOtherUserNoteReturns404() throws Exception {
        UUID noteId = UUID.randomUUID();
        when(noteService.getNote(testUser.getId(), noteId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));

        mockMvc.perform(get("/api/me/notes/" + noteId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Note not found"));
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps MaxUploadSizeExceededException to 413 Payload Too Large")
    void oversizedFileUploadReturns413() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.md", "text/markdown", "big".getBytes()
        );

        when(noteService.upload(eq(testUser.getId()), any()))
                .thenAnswer(inv -> {
                    throw new MaxUploadSizeExceededException(1048576);
                });

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("Payload Too Large"));
    }
}
