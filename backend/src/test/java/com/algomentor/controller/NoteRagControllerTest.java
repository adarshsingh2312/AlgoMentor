package com.algomentor.controller;

import com.algomentor.config.AiProperties;
import com.algomentor.config.DateTimeConfig;
import com.algomentor.config.JwtAuthFilter;
import com.algomentor.config.JwtProperties;
import com.algomentor.config.NotesProperties;
import com.algomentor.config.OAuth2AuthenticationSuccessHandler;
import com.algomentor.config.OAuth2Properties;
import com.algomentor.config.SecurityConfig;
import com.algomentor.domain.NoteDocument;
import com.algomentor.domain.NoteStatus;
import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.AiIngestResponse;
import com.algomentor.dto.AskNotesRequest;
import com.algomentor.dto.AskNotesResponseDto;
import com.algomentor.exception.AiUnavailableException;
import com.algomentor.exception.GlobalExceptionHandler;
import com.algomentor.repository.NoteDocumentRepository;
import com.algomentor.repository.UserRepository;
import com.algomentor.service.AiServiceClient;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NoteController.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        JwtProperties.class,
        OAuth2Properties.class,
        OAuth2AuthenticationSuccessHandler.class,
        GlobalExceptionHandler.class,
        DateTimeConfig.class,
        NoteService.class,
        NotesProperties.class,
        AiProperties.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-at-least-32-characters-long-for-testing-purposes!",
        "app.jwt.expiry-minutes=60",
        "app.oauth2.redirect-uri=http://localhost:5173/auth/callback",
        "app.ai.base-url=http://localhost:8000",
        "app.ai.service-key=test-ai-service-key-at-least-32-characters!",
        "app.timezone=UTC"
})
class NoteRagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NoteDocumentRepository noteDocumentRepository;
    @MockBean private UserRepository userRepository;
    @MockBean private AiServiceClient aiServiceClient;
    @MockBean private ClientRegistrationRepository clientRegistrationRepository;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("developer@example.com");
        testUser.setName("Developer");
        testUser.setRole(Role.USER);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));

        token = "Bearer " + jwtService.generate(testUser);
    }

    @Test
    @DisplayName("Upload note with successful AI ingestion returns 201 with status INDEXED")
    void uploadNoteSuccessSetsStatusIndexed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dijkstra.md", "text/markdown", "# Dijkstra Shortest Path".getBytes()
        );

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), any()))
                .thenReturn(Optional.empty());
        when(noteDocumentRepository.countByUserId(testUser.getId())).thenReturn(0L);

        // Mock saving and returning entity
        when(noteDocumentRepository.save(any(NoteDocument.class))).thenAnswer(inv -> {
            NoteDocument doc = inv.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(UUID.randomUUID());
            }
            return doc;
        });

        // Mock finding for status update
        when(noteDocumentRepository.findById(any())).thenAnswer(inv -> {
            NoteDocument doc = new NoteDocument();
            doc.setId(inv.getArgument(0));
            doc.setUser(testUser);
            doc.setDisplayName("dijkstra.md");
            doc.setContent("# Dijkstra Shortest Path");
            return Optional.of(doc);
        });

        when(aiServiceClient.ingest(any(), eq(testUser.getId()), eq("dijkstra.md"), any()))
                .thenReturn(new AiIngestResponse(UUID.randomUUID(), 1, "INGESTED"));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    @DisplayName("Upload note with AI failure returns 201 with status FAILED")
    void uploadNoteAiFailureReturns201WithStatusFailed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "graphs.md", "text/markdown", "# Graph Algorithms".getBytes()
        );

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), any()))
                .thenReturn(Optional.empty());
        when(noteDocumentRepository.countByUserId(testUser.getId())).thenReturn(0L);

        when(noteDocumentRepository.save(any(NoteDocument.class))).thenAnswer(inv -> {
            NoteDocument doc = inv.getArgument(0);
            if (doc.getId() == null) {
                doc.setId(UUID.randomUUID());
            }
            return doc;
        });

        when(noteDocumentRepository.findById(any())).thenAnswer(inv -> {
            NoteDocument doc = new NoteDocument();
            doc.setId(inv.getArgument(0));
            doc.setUser(testUser);
            doc.setDisplayName("graphs.md");
            doc.setContent("# Graph Algorithms");
            return Optional.of(doc);
        });

        // Simulate AI service failure
        when(aiServiceClient.ingest(any(), any(), any(), any()))
                .thenThrow(new AiUnavailableException("AI service connection refused"));

        mockMvc.perform(multipart("/api/me/notes")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("POST /api/me/notes/ask without token returns 401 Unauthorized")
    void askNotesWithoutTokenReturns401() throws Exception {
        AskNotesRequest request = new AskNotesRequest("How does Dijkstra work?");

        mockMvc.perform(post("/api/me/notes/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE note when AI is down returns 502 and keeps database row")
    void deleteNoteWhenAiDownReturns502AndKeepsRow() throws Exception {
        UUID noteId = UUID.randomUUID();
        NoteDocument doc = new NoteDocument();
        doc.setId(noteId);
        doc.setUser(testUser);

        when(noteDocumentRepository.findByIdAndUserId(noteId, testUser.getId()))
                .thenReturn(Optional.of(doc));

        // AI service delete fails
        doThrow(new AiUnavailableException("AI service timed out"))
                .when(aiServiceClient).delete(noteId, testUser.getId());

        mockMvc.perform(delete("/api/me/notes/" + noteId)
                        .header("Authorization", token))
                .andExpect(status().isBadGateway());

        // Verifies the row was NEVER deleted from the repository
        verify(noteDocumentRepository, never()).delete(any());
    }
}

