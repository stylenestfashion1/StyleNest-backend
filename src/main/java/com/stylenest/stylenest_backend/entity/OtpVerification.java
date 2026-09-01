package com.stylenest.stylenest_backend.entity;

import java.time.LocalDateTime;

import com.stylenest.stylenest_backend.enums.OtpPurpose;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "otp_verification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Column(nullable = false)
    private boolean verified;
    
    private Integer attempts;

    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer requestCount = 1;

    @Column(nullable = false)
    private LocalDateTime firstRequestTime;
    
    @Column(nullable = false)
    
    private OtpPurpose purpose;
    
    
}