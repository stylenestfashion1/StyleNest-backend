package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stylenest.stylenest_backend.entity.BulkAccessToken;

public interface BulkAccessTokenRepository extends JpaRepository<BulkAccessToken, Long> {

    Optional<BulkAccessToken> findByToken(String token);

    boolean existsByToken(String token);

    List<BulkAccessToken> findAllByOrderByCreatedAtAsc();
}
