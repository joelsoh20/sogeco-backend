package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {

    @EntityGraph(attributePaths = "requestedBy")
    Page<GeneratedReport> findByOrderByGeneratedAtDesc(Pageable pageable);

    List<GeneratedReport> findByReportTypeOrderByGeneratedAtDesc(ReportType type);

    @Modifying
    @Query("DELETE FROM GeneratedReport r WHERE r.generatedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
