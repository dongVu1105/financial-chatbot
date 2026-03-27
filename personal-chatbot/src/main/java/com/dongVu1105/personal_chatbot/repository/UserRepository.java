package com.dongVu1105.personal_chatbot.repository;


import com.dongVu1105.personal_chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    public Optional<User> findByUsername (String username);
}
