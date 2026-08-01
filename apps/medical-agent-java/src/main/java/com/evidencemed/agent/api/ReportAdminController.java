package com.evidencemed.agent.api;

import com.evidencemed.agent.domain.report.MedicalReport;
import com.evidencemed.agent.infrastructure.persistence.MedicalReportRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/admin/v1/reports")
public class ReportAdminController {
    private final MedicalReportRepository reports;
    public ReportAdminController(MedicalReportRepository reports) { this.reports = reports; }

    @GetMapping("/{reportId}")
    public Mono<MedicalReport> find(@PathVariable String reportId) {
        return Mono.fromCallable(() -> reports.findById(reportId)
                        .orElseThrow(() -> new IllegalArgumentException("医疗报告不存在")))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
