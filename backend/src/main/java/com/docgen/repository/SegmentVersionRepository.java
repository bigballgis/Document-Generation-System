package com.docgen.repository;

import com.docgen.entity.SegmentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SegmentVersion} entities.
 */
public interface SegmentVersionRepository extends JpaRepository<SegmentVersion, Long> {

    List<SegmentVersion> findByTemplateIdAndSegmentNameOrderByVersionNumberDesc(
            Long templateId, String segmentName);

    @Query("SELECT MAX(v.versionNumber) FROM SegmentVersion v " +
           "WHERE v.templateId = :templateId AND v.segmentName = :segmentName")
    Optional<Integer> findMaxVersionNumber(
            @Param("templateId") Long templateId,
            @Param("segmentName") String segmentName);

    Optional<SegmentVersion> findByTemplateIdAndSegmentNameAndVersionNumber(
            Long templateId, String segmentName, Integer versionNumber);
}
