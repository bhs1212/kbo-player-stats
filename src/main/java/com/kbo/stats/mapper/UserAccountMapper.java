package com.kbo.stats.mapper;

import com.kbo.stats.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserAccountMapper {

    Optional<UserAccount> findByUsername(String username);

    void insert(UserAccount user);

    boolean existsByUsername(String username);

    void updateFavoriteTeam(@Param("username") String username,
                            @Param("favoriteTeam") String favoriteTeam);
}
