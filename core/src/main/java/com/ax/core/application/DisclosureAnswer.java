package com.ax.core.application;

import com.ax.core.disclosure.Question;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고지 문항 응답 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(nullable = false)
    private boolean answer;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DisclosureDetail> details = new ArrayList<>();

    public DisclosureAnswer(Question question, boolean answer) {
        this.question = question;
        this.answer = answer;
    }

    public void addDetail(DisclosureDetail detail) {
        detail.attachTo(this);
        details.add(detail);
    }

    void attachTo(Application application) {
        this.application = application;
    }
}
