package com.portfolio.jobcrawler.domain.coverletter.repository;

import com.portfolio.jobcrawler.domain.coverletter.entity.CoverLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    @Query("SELECT c FROM CoverLetter c WHERE " +
            "(:keyword IS NULL OR LOWER(c.company) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')) " +
            "OR LOWER(c.position) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')) " +
            "OR LOWER(c.content) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))) " +
            "AND (:school IS NULL OR LOWER(c.school) LIKE LOWER(CONCAT('%', CAST(:school AS text), '%')))")
    Page<CoverLetter> search(@Param("keyword") String keyword, @Param("school") String school, Pageable pageable);
}
