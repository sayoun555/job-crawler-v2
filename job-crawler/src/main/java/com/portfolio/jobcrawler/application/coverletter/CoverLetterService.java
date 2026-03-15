package com.portfolio.jobcrawler.application.coverletter;

import com.portfolio.jobcrawler.domain.coverletter.entity.CoverLetter;
import com.portfolio.jobcrawler.domain.coverletter.repository.CoverLetterRepository;
import com.portfolio.jobcrawler.infrastructure.crawler.CoverLetterCrawler;
import com.portfolio.jobcrawler.infrastructure.crawler.dto.CrawledCoverLetterData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverLetterService {

    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterCrawler coverLetterCrawler;

    @Transactional
    public int crawlAndSave(int maxPages) {
        List<CrawledCoverLetterData> crawled = coverLetterCrawler.crawl(maxPages);
        int saved = 0;
        for (CrawledCoverLetterData data : crawled) {
            if (coverLetterRepository.existsBySourceUrl(data.getSourceUrl())) continue;
            coverLetterRepository.save(CoverLetter.builder()
                    .company(data.getCompany())
                    .position(data.getPosition())
                    .period(data.getPeriod())
                    .companyType(data.getCompanyType())
                    .careerType(data.getCareerType())
                    .school(data.getSchool())
                    .major(data.getMajor())
                    .gpa(data.getGpa())
                    .specs(data.getSpecs())
                    .content(data.getContent())
                    .scrapCount(data.getScrapCount())
                    .sourceUrl(data.getSourceUrl())
                    .build());
            saved++;
        }
        log.info("[자소서] 크롤링 완료 - 수집: {}건, 신규저장: {}건", crawled.size(), saved);
        return saved;
    }

    public Page<CoverLetter> search(String keyword, String school, Pageable pageable) {
        return coverLetterRepository.search(keyword, school, pageable);
    }

    public CoverLetter getById(Long id) {
        return coverLetterRepository.findById(id).orElseThrow(() -> new RuntimeException("자소서를 찾을 수 없습니다."));
    }

    public long count() {
        return coverLetterRepository.count();
    }

    @Transactional
    public void deleteAll() {
        coverLetterRepository.deleteAllInBatch();
    }
}
