package com.grid07.coreapi.guardrails.repository;

import com.grid07.coreapi.guardrails.entity.Bot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotRepository extends JpaRepository<Bot, Long> {

    Optional<Bot> findByName(String name);
    boolean existsByName(String name);
}
