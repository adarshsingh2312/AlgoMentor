package com.algomentor.service;

import com.algomentor.domain.Role;
import com.algomentor.domain.User;
import com.algomentor.dto.SyncResult;
import com.algomentor.service.CodeforcesService.CfSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncServiceTest {

    private CodeforcesService codeforcesService;
    private UpsertService upsertService;
    private SyncService syncService;

    @BeforeEach
    void setUp() {
        codeforcesService = mock(CodeforcesService.class);
        upsertService = mock(UpsertService.class);
        syncService = new SyncService(codeforcesService, upsertService);
    }

    @Test
    @DisplayName("syncUserSubmissions - stops paging early when a page produces zero new inserts (stop-at-known)")
    void syncUserSubmissions_stopsPagingWhenZeroNewInserts() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("tourist@example.com");
        user.setName("Gennady");
        user.setRole(Role.USER);
        user.setCfHandle("tourist");

        // Create mock 500 items for page 1 and page 2
        List<CfSubmission> page1 = Collections.nCopies(500, mock(CfSubmission.class));
        List<CfSubmission> page2 = Collections.nCopies(500, mock(CfSubmission.class));

        when(codeforcesService.fetchSubmissions("tourist", 1, 500)).thenReturn(page1);
        when(codeforcesService.fetchSubmissions("tourist", 501, 500)).thenReturn(page2);

        // Page 1 inserts 5 new submissions
        when(upsertService.upsertSubmissions(user, page1))
                .thenReturn(new SyncResult(0, 0, 5, 495));

        // Page 2 inserts 0 new submissions (all known) -> triggers early exit
        when(upsertService.upsertSubmissions(user, page2))
                .thenReturn(new SyncResult(0, 0, 0, 500));

        SyncResult total = syncService.syncUserSubmissions(user);

        assertThat(total.submissionsInserted()).isEqualTo(5);
        assertThat(total.submissionsSkipped()).isEqualTo(995);

        // Verify page 1 and page 2 were requested
        verify(codeforcesService, times(1)).fetchSubmissions("tourist", 1, 500);
        verify(codeforcesService, times(1)).fetchSubmissions("tourist", 501, 500);

        // Crucial: verify page 3 (offset 1001) was NEVER requested due to stop-at-known optimization
        verify(codeforcesService, never()).fetchSubmissions(eq("tourist"), eq(1001), anyInt());
    }
}

