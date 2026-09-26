package com.example.erp.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {
    List<RoleDefinition> findAllByOrderByNameAsc();
    Optional<RoleDefinition> findByNameIgnoreCase(String name);
    long countByName(String name);
}
