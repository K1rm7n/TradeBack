package com.tradeback.repository;

import com.tradeback.model.UserHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    @Query("SELECT uh FROM UserHistory uh JOIN FETCH uh.user " +
            "WHERE uh.user.id = :userId ORDER BY uh.requestTime DESC")
    List<UserHistory> findByUserIdOrderByRequestTimeDesc(@Param("userId") Long userId);

    List<UserHistory> findBySymbolOrderByRequestTimeDesc(String symbol);

    // Новый метод для получения последних N записей (оптимизация)
    @Query("SELECT uh FROM UserHistory uh JOIN FETCH uh.user " +
            "WHERE uh.user.id = :userId ORDER BY uh.requestTime DESC LIMIT :limit")
    List<UserHistory> findTopNByUserIdOrderByRequestTimeDesc(@Param("userId") Long userId,
                                                             @Param("limit") int limit);
}