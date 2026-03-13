package com.owlynbackend.internal.model;
import com.owlynbackend.internal.model.enums.InterviewMode;
import com.owlynbackend.internal.model.enums.InterviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.util.Map;
import java.util.UUID;
@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private String title;

    @Column(name = "candidate_name")
    private String candidateName;

    @Column(name = "candidate_email")
    private String candidateEmail;

    @Column(name = "access_code", length = 6, unique = true, nullable = false)
    private String accessCode;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes = 45;

    // Maps directly to Postgres JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools_enabled", columnDefinition = "jsonb")
   private Map<String, Boolean> toolsEnabled;

    @Column(name = "ai_instructions", columnDefinition = "TEXT")
    private String aiInstructions;

    @Column(name = "generated_questions", columnDefinition = "TEXT")
    private String generatedQuestions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.UPCOMING;


    // Add this inside Interview.java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id")
    private AIPersona persona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InterviewMode mode = InterviewMode.STANDARD;
}