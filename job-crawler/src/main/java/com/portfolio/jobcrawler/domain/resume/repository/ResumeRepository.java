package com.portfolio.jobcrawler.domain.resume.repository;

import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import com.portfolio.jobcrawler.domain.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /** 마스터(고유) 이력서 조회 */
    Optional<Resume> findByUserIdAndSourceSiteIsNull(Long userId);

    /** 특정 사이트의 이력서 목록 (여러 개 가능) */
    List<Resume> findByUserIdAndSourceSite(Long userId, SourceSite sourceSite);

    /** 특정 사이트의 첫 번째(최신) 이력서 */
    Optional<Resume> findFirstByUserIdAndSourceSiteOrderByUpdatedAtDesc(Long userId, SourceSite sourceSite);

    /** 유저의 모든 이력서 (마스터 + 사이트별) */
    List<Resume> findAllByUserId(Long userId);
}
