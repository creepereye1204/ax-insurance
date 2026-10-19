package com.ax.core.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ax.core.ai.AiReviewClient;
import com.ax.core.ai.AiReviewException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class ClaimAiReviewerTest {

    ClaimRepository repository = mock(ClaimRepository.class);
    AiReviewClient client = mock(AiReviewClient.class);
    TransactionOperations tx = mock(TransactionOperations.class);
    ClaimAiReviewer reviewer = new ClaimAiReviewer(repository, client, tx);
    Claim claim = ClaimTest.claim(50_000);

    @BeforeEach
    void setUp() {
        when(repository.findById(1L)).thenReturn(Optional.of(claim));
        when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void AI_응답을_받으면_검토완료_상태가_된다() {
        when(client.review(anyString())).thenReturn(new AiReviewClient.Review("4만원 지급 예상", 7L));

        reviewer.review(1L);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.AI_REVIEWED);
        assertThat(claim.getAiRunId()).isEqualTo(7L);
    }

    @Test
    void AI_장애시_수동심사로_전환된다() {
        when(client.review(anyString())).thenThrow(new AiReviewException("read timeout", null));

        reviewer.review(1L);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.MANUAL_REVIEW);
        assertThat(claim.getAiOpinion()).contains("read timeout");
    }

    @Test
    void 질문에_약관문서명과_청구내용이_들어간다() {
        assertThat(ClaimAiReviewer.buildQuestion(claim))
                .contains("샘플_실손의료비보험", "통원", "급성 위염", "50,000원");
    }
}
