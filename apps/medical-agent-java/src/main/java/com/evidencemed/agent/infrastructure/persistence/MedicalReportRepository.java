package com.evidencemed.agent.infrastructure.persistence;

import com.evidencemed.agent.domain.report.MedicalReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalReportRepository extends JpaRepository<MedicalReport, String> {
    List<MedicalReport> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
