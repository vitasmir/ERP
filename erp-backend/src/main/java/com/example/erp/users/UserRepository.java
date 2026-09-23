package com.example.erp.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<ErpUser, UUID> {
    List<ErpUser> findAllByOrderByFullNameAsc();
}