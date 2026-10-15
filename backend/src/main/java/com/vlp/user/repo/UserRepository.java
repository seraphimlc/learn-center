package com.vlp.user.repo;

import com.vlp.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByRole(String role);
    long countByEnabledTrue();

    Optional<User> findByWechatOpenId(String wechatOpenId);
    Optional<User> findByWechatUnionId(String wechatUnionId);

    @Query("SELECT u FROM User u WHERE (:kw IS NULL OR :kw = '' OR u.username LIKE %:kw% OR u.nickname LIKE %:kw% OR u.email LIKE %:kw%) AND (:role IS NULL OR :role = '' OR u.role = :role)")
    Page<User> search(@Param("kw") String kw, @Param("role") String role, Pageable pageable);
}
