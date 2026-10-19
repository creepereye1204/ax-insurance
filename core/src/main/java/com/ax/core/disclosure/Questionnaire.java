package com.ax.core.disclosure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계약 전 알릴의무 질문지. 문항이 바뀌면 기존 버전을 수정하지 않고 새 버전을 만든다.
 * 청약은 작성 시점의 질문지 버전을 참조해 "그때 무엇을 물었는가"를 보존한다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"code", "version"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Questionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<Question> questions = new ArrayList<>();

    public Questionnaire(String code, int version, String title, LocalDate effectiveFrom) {
        this.code = code;
        this.version = version;
        this.title = title;
        this.effectiveFrom = effectiveFrom;
    }

    public Questionnaire addQuestion(String code, String text, int lookbackMonths, boolean detailRequired) {
        questions.add(new Question(this, questions.size() + 1, code, text, lookbackMonths, detailRequired));
        return this;
    }
}
