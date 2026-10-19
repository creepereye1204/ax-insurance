package com.ax.core.disclosure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionnaire_id")
    private Questionnaire questionnaire;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 500)
    private String text;

    /** 고지 대상 기간 (0 이면 "현재") */
    @Column(nullable = false)
    private int lookbackMonths;

    /** "예" 응답 시 질병/치료 상세 입력이 필요한지 */
    @Column(nullable = false)
    private boolean detailRequired;

    Question(Questionnaire questionnaire, int seq, String code, String text, int lookbackMonths, boolean detailRequired) {
        this.questionnaire = questionnaire;
        this.seq = seq;
        this.code = code;
        this.text = text;
        this.lookbackMonths = lookbackMonths;
        this.detailRequired = detailRequired;
    }
}
