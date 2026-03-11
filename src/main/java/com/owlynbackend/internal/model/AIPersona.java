package com.owlynbackend.internal.model;



import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_personas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPersona {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // A persona belongs to a specific workspace (company)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name; // e.g., "Atlas-7"

    @Column(name = "role_title")
    private String roleTitle; // e.g., "SENIOR TECHNICAL EVALUATOR"

    // SLIDERS (0 to 100)
    @Column(name = "empathy_score")
    private Integer empathyScore; // 75 (Strictness)

    @Column(name = "analytical_depth")
    private Integer analyticalDepth; // 90

    @Column(name = "directness_score")
    private Integer directnessScore; // 60 (Collaborative)

    // TONE
    @Column(name = "tone")
    private String tone; // MENTOR, ARCHITECT, INQUISITOR

    // DOMAIN EXPERTISE (Tags)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_expertise", columnDefinition = "jsonb")
    private List<String> domainExpertise; // ["KUBERNETES", "REACT ARCHITECTURE"]

    // THE MASSIVE CONTEXT HACK (Extracted from PDFs/DOCX)
    @Column(name = "knowledge_base_text", columnDefinition = "TEXT")
    private String knowledgeBaseText;
}