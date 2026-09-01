package com.stylenest.stylenest_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stylenest.stylenest_backend.entity.Address;
import com.stylenest.stylenest_backend.entity.User;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUser(User user);

    Optional<Address> findByUserAndIsDefaultTrue(User user);
}