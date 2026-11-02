package com.algomentor.service;

import com.algomentor.config.NotesProperties;
import com.algomentor.domain.NoteDocument;
import com.algomentor.domain.NoteStatus;
import com.algomentor.domain.User;
import com.algomentor.dto.NoteDocumentDetailDto;
import com.algomentor.dto.NoteDocumentListDto;
import com.algomentor.dto.NoteUploadResult;
import com.algomentor.repository.NoteDocumentRepository;
import com.algomentor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteDocumentRepository noteDocumentRepository;
    @Mock private UserRepository userRepository;
    @Spy private NotesProperties notesProperties = new NotesProperties();

    @InjectMocks
    private NoteService noteService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Upload valid markdown file creates note document and returns created result")
    void uploadValidMarkdownFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.md",
                "text/markdown",
                "# Binary Search Notes".getBytes(StandardCharsets.UTF_8)
        );

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.empty());
        when(noteDocumentRepository.countByUserId(testUser.getId())).thenReturn(5L);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(noteDocumentRepository.save(any(NoteDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteUploadResult result = noteService.upload(testUser.getId(), file);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.note().displayName()).isEqualTo("notes.md");
        assertThat(result.note().content()).isEqualTo("# Binary Search Notes");
        assertThat(result.note().status()).isEqualTo(NoteStatus.UPLOADED);

        ArgumentCaptor<NoteDocument> captor = ArgumentCaptor.forClass(NoteDocument.class);
        verify(noteDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(testUser);
    }

    @Test
    @DisplayName("Upload duplicate file content returns existing note with isDuplicate true")
    void uploadDuplicateReturnsExisting() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.md",
                "text/markdown",
                "identical content".getBytes(StandardCharsets.UTF_8)
        );

        NoteDocument existing = new NoteDocument();
        existing.setId(UUID.randomUUID());
        existing.setDisplayName("earlier-notes.md");
        existing.setContent("identical content");
        existing.setStatus(NoteStatus.UPLOADED);

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.of(existing));

        NoteUploadResult result = noteService.upload(testUser.getId(), file);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.note().displayName()).isEqualTo("earlier-notes.md");
        verify(noteDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Upload non-markdown/non-text file (.exe) throws 400 Bad Request")
    void uploadInvalidExtensionThrows400() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                "binary content".getBytes()
        );

        assertThatThrownBy(() -> noteService.upload(testUser.getId(), file))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Upload empty file throws 400 Bad Request")
    void uploadEmptyFileThrows400() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThatThrownBy(() -> noteService.upload(testUser.getId(), file))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Upload when quota of 50 documents reached throws 400 Bad Request")
    void uploadExceedingQuotaThrows400() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc51.txt",
                "text/plain",
                "new doc".getBytes()
        );

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.empty());
        when(noteDocumentRepository.countByUserId(testUser.getId())).thenReturn(50L);

        assertThatThrownBy(() -> noteService.upload(testUser.getId(), file))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Path traversal sequences in filename are stripped for displayName")
    void filenamePathTraversalIsSanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../etc/passwd.txt",
                "text/plain",
                "some content".getBytes()
        );

        when(noteDocumentRepository.findByUserIdAndContentHash(eq(testUser.getId()), anyString()))
                .thenReturn(Optional.empty());
        when(noteDocumentRepository.countByUserId(testUser.getId())).thenReturn(0L);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(noteDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NoteUploadResult result = noteService.upload(testUser.getId(), file);

        assertThat(result.note().displayName()).isEqualTo("passwd.txt");
    }

    @Test
    @DisplayName("Getting another user's note throws 404 Not Found")
    void getNoteWrongUserThrows404() {
        UUID unknownOrOtherUserNoteId = UUID.randomUUID();
        when(noteDocumentRepository.findByIdAndUserId(unknownOrOtherUserNoteId, testUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNote(testUser.getId(), unknownOrOtherUserNoteId))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Deleting another user's note throws 404 Not Found")
    void deleteNoteWrongUserThrows404() {
        UUID unknownOrOtherUserNoteId = UUID.randomUUID();
        when(noteDocumentRepository.findByIdAndUserId(unknownOrOtherUserNoteId, testUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(testUser.getId(), unknownOrOtherUserNoteId))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode() == HttpStatus.NOT_FOUND);
    }
}

