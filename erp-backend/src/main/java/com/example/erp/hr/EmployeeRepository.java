package com.example.erp.hr;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    @EntityGraph(attributePaths = "team")
    List<Employee> findAllByOrderByEmploymentStartDateDesc();
    boolean existsByTeam_Id(UUID teamId);
    boolean existsByDeputy_Id(UUID employeeId);
}