package com.owlynbackend.internal.repository;

import com.owlynbackend.internal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    // Basic CRUD is already included
    Optional<User> findByEmail(String email);
}
