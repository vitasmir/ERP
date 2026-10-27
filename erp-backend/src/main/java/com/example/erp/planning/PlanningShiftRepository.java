package com.example.erp.planning;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanningShiftRepository extends JpaRepository<PlanningShift, UUID> {
    List<PlanningShift> findAllByOrderByStartAtAsc();
}