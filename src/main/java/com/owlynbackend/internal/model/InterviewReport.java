package com.owlynbackend.internal.model;
import com.owlynbackend.internal.model.enums.FinalDecision;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;
@Entity
@Table(name = "interview_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "candidate_email", nullable = false)
    private String candidateEmail;

    @Column(name = "candidate_name")
    private String candidateName;

    private Integer score;

    @Column(name = "behavioral_notes", columnDefinition = "TEXT")
    private String behavioralNotes;

    @Column(name = "code_output", columnDefinition = "TEXT")
    private String codeOutput;

    // Stores Gemini's proctoring flags as a native Postgres JSONB array/object
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "behavior_flags", columnDefinition = "jsonb")
    private Map<String, Object> behaviorFlags;

    @Column(name = "human_feedback", columnDefinition = "TEXT")
    private String humanFeedback;


    // Inside InterviewReport.java

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", nullable = false)
    @Builder.Default
    private FinalDecision finalDecision = FinalDecision.PENDING;
}
