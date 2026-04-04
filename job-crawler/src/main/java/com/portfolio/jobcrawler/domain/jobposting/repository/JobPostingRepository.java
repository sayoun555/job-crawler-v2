package com.portfolio.jobcrawler.domain.jobposting.repository;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.vo.ApplicationMethod;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    boolean existsByUrl(String url);

    Optional<JobPosting> findByUrl(String url);

    Page<JobPosting> findBySourceAndClosedFalse(SourceSite source, Pageable pageable);

    @Query("SELECT j FROM JobPosting j WHERE j.closed = false " +
            "AND (:source IS NULL OR j.source = :source) " +
            "AND (:jobCategory IS NULL OR j.jobCategory = :jobCategory) " +
            "AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')) " +
            "   OR LOWER(j.company) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))) " +
            "AND (:career IS NULL OR LOWER(j.career) LIKE LOWER(CONCAT('%', CAST(:career AS text), '%'))) " +
            "AND (:education IS NULL OR LOWER(j.education) LIKE LOWER(CONCAT('%', CAST(:education AS text), '%'))) " +
            "AND (:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', CAST(:location AS text), '%'))) " +
            "AND (:applicationMethod IS NULL OR j.applicationMethod = :applicationMethod) " +
            "AND (:tag IS NULL OR LOWER(CAST(j.techStack AS text)) LIKE LOWER(CONCAT('%', CAST(:tag AS text), '%')))")
    Page<JobPosting> searchJobs(
            @Param("source") SourceSite source,
            @Param("keyword") String keyword,
            @Param("jobCategory") String jobCategory,
            @Param("career") String career,
            @Param("education") String education,
            @Param("location") String location,
            @Param("applicationMethod") ApplicationMethod applicationMethod,
            @Param("tag") String tag,
            Pageable pageable);

    long countBySourceAndClosedFalse(SourceSite source);

    List<JobPosting> findBySource(SourceSite source);

    @Transactional
    @Modifying
    @Query("UPDATE JobPosting j SET j.closed = true WHERE j.closed = false AND j.deadline IS NOT NULL AND j.deadline < :today")
    int closeExpired(@Param("today") LocalDate today);

    Page<JobPosting> findByClosedTrue(Pageable pageable);

    long countByClosedTrue();

    @Query("SELECT j FROM JobPosting j WHERE j.closed = false AND j.deadline IS NULL")
    List<JobPosting> findOpenWithNoDeadline();

    @Query("SELECT j FROM JobPosting j WHERE j.closed = false AND j.deadline IS NULL AND j.createdAt < :before")
    List<JobPosting> findStaleNoDeadlinePostings(@Param("before") java.time.LocalDateTime before);

    @Query("SELECT j.career, COUNT(j) FROM JobPosting j WHERE j.closed = false GROUP BY j.career ORDER BY COUNT(j) DESC")
    List<Object[]> countByCareerGroup();

    @Query("SELECT j.education, COUNT(j) FROM JobPosting j WHERE j.closed = false GROUP BY j.education ORDER BY COUNT(j) DESC")
    List<Object[]> countByEducationGroup();

    @Query("SELECT j.location, COUNT(j) FROM JobPosting j WHERE j.closed = false GROUP BY j.location ORDER BY COUNT(j) DESC")
    List<Object[]> countByLocationGroup();

    @Query("SELECT j FROM JobPosting j WHERE (j.description IS NULL OR j.description = '') AND (j.requirements IS NULL OR j.requirements = '')")
    List<JobPosting> findEmptyPostings();
}
