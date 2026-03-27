package com.dongVu1105.personal_chatbot.repository;


import com.dongVu1105.personal_chatbot.entity.InvalidatedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {
}
