package io.releasehub.infrastructure.persistence.version;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * VersionPolicy JPA Repository
 */
public interface VersionPolicyJpaRepository extends JpaRepository<VersionPolicyJpaEntity, String> {
    @Query("""
            select v from VersionPolicyJpaEntity v
            where (:keyword is null
                or lower(v.name) like lower(concat('%', :keyword, '%'))
                or lower(v.scheme) like lower(concat('%', :keyword, '%'))
                or lower(v.bumpRule) like lower(concat('%', :keyword, '%')))
            """)
    Page<VersionPolicyJpaEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
