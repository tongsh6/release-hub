package io.releasehub.infrastructure.persistence.user;

import io.releasehub.domain.user.User;
import io.releasehub.application.user.UserPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter：基础设施层对 Port 的实现
 */
@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserPort {

    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        return toDomain(jpaRepository.save(toEntity(user)));
    }

    @Override
    public Optional<User> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
            entity.getId(),
            entity.getUsername(),
            entity.getPasswordHash(),
            entity.getDisplayName(),
            entity.isEnabled()
        );
    }

    private UserJpaEntity toEntity(User domain) {
        return new UserJpaEntity(
            domain.getId(),
            domain.getUsername(),
            domain.getPasswordHash(),
            domain.getDisplayName(),
            domain.isEnabled()
        );
    }
}
