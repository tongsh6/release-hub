package io.releasehub.application.user;

import io.releasehub.domain.user.User;

import java.util.Optional;

/**
 * Port/Gateway：用例层对外部能力的抽象
 */
public interface UserPort {
    Optional<User> findByUsername(String username);
    User save(User user);
    Optional<User> findById(String id);

    /** 在当前事务内串行化首次账户初始化，锁持续到事务结束。 */
    void lockForInitialization();

    boolean existsAny();
}
