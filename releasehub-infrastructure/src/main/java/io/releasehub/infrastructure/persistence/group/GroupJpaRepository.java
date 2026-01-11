package io.releasehub.infrastructure.persistence.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupJpaRepository extends JpaRepository<GroupJpaEntity, String> {
    List<GroupJpaEntity> findByParentCode(String parentCode);
    List<GroupJpaEntity> findByParentCodeIsNull();
    Optional<GroupJpaEntity> findByCode(String code);
    long countByParentCode(String parentCode);
    void deleteByCode(String code);
}
