package com.portfolio.jobcrawler.domain.project.vo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 일급 컬렉션: 프로젝트 이미지 URL 목록(ProjectImages)
 *
 * 단순 List<String>을 래핑하여 이미지 관련 비즈니스 규칙
 * (최대 개수 제한, 중복 방지 등)을 캡슐화한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectImages {

    private static final int MAX_IMAGE_COUNT = 10;

    @ElementCollection
    @CollectionTable(name = "project_images", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "image_url", length = 1000)
    private List<String> urls = new ArrayList<>();

    private ProjectImages(List<String> urls) {
        this.urls = new ArrayList<>(urls);
    }

    // === 팩토리 메서드 ===

    public static ProjectImages empty() {
        return new ProjectImages(new ArrayList<>());
    }

    public static ProjectImages of(List<String> urls) {
        if (urls == null) return empty();
        if (urls.size() > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException(
                    String.format("프로젝트 이미지는 최대 %d장까지 등록할 수 있습니다.", MAX_IMAGE_COUNT));
        }
        return new ProjectImages(urls);
    }

    // === 도메인 비즈니스 로직 ===

    /** 이미지 추가 (최대 개수 검증 포함) */
    public void add(String imageUrl) {
        if (urls.size() >= MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException(
                    String.format("프로젝트 이미지는 최대 %d장까지 등록할 수 있습니다.", MAX_IMAGE_COUNT));
        }
        if (!urls.contains(imageUrl)) {
            urls.add(imageUrl);
        }
    }

    /** 이미지 제거 */
    public void remove(String imageUrl) {
        urls.remove(imageUrl);
    }

    /** 읽기 전용 목록 반환 */
    public List<String> toList() {
        return Collections.unmodifiableList(urls);
    }

    /** 이미지 개수 */
    public int size() {
        return urls.size();
    }

    /** 이미지가 비어있는지 */
    public boolean isEmpty() {
        return urls.isEmpty();
    }

    /** 특정 이미지 포함 여부 */
    public boolean contains(String imageUrl) {
        return urls.contains(imageUrl);
    }
}
