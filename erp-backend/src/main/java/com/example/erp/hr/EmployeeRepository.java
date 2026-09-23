package com.example.erp.hr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findAllByOrderByEmploymentStartDateDesc();
}