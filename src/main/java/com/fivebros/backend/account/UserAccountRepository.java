package com.fivebros.backend.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fivebros.backend.security.Role;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.email = :email")
    Optional<UserAccount> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("""
            select user from UserAccount user
            where (:status is null or user.status = :status)
              and (:search = ''
                   or lower(user.email) like concat('%', :search, '%')
                   or lower(user.fullName) like concat('%', :search, '%'))
            """)
    Page<UserAccount> search(@Param("search") String search, @Param("status") AccountStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select user from UserAccount user
            where :role member of user.roles
            order by user.id
            """)
    List<UserAccount> lockUsersWithRole(@Param("role") Role role);

    @Query("select count(user) from UserAccount user where :role member of user.roles")
    long countUsersWithRole(@Param("role") Role role);

    @Query("""
            select count(user) from UserAccount user
            where user.status = com.fivebros.backend.account.AccountStatus.ACTIVE
              and :role member of user.roles
            """)
    long countActiveUsersWithRole(@Param("role") Role role);
}
