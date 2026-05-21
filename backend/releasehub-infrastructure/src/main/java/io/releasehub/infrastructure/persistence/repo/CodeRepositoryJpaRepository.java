package io.releasehub.infrastructure.persistence.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepositoryJpaEntity, String> {
    void deleteById(String id);

    Page<CodeRepositoryJpaEntity> findByNameContainingIgnoreCaseOrCloneUrlContainingIgnoreCase(String name, String cloneUrl, Pageable pageable);

    @Query("""
            select r from CodeRepositoryJpaEntity r
            where r.groupCode in :groupCodes
              and (
                :keyword is null
                or :keyword = ''
                or lower(r.name) like lower(concat('%', :keyword, '%'))
                or lower(r.cloneUrl) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<CodeRepositoryJpaEntity> searchByGroupCodesAndKeyword(Collection<String> groupCodes, String keyword, Pageable pageable);
}
