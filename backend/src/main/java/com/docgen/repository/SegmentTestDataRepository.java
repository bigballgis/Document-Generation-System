package com.docgen.repository;

import com.docgen.entity.SegmentTestData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SegmentTestData} entities.
 */
public interface SegmentTestDataRepository extends JpaRepository<SegmentTestData, Long> {

    List<SegmentTestData> findBySegmentIdOrderByCreatedAtDesc(Long segmentId);

    void deleteBySegmentId(Long segmentId);
}
