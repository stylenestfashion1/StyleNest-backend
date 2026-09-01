package com.stylenest.stylenest_backend.entity;

import org.hibernate.annotations.UpdateTimestamp;

import com.stylenest.stylenest_backend.enums.Gender;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * The image used by one of the three "ScrollExpand" cinematic transitions
 * on a gender's storefront -- one row per (gender, step). Upserted, never
 * duplicated: see ScrollDownImageServiceImpl.upsert.
 */
@Entity
@Table(
        name = "scroll_down_images",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scroll_down_image_gender_step",
                columnNames = {"gender", "step"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrollDownImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private Integer step;

    @Column(nullable = false)
    private String imageUrl;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
