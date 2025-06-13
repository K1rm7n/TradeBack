package com.tradeback.repository;

import com.tradeback.model.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    List<Signal> findBySymbolOrderByDateAsc(String symbol);

    // Один универсальный метод вместо двух дублированных
    @Query("SELECT s FROM Signal s WHERE s.symbol = :symbol " +
            "AND s.date BETWEEN :startDate AND :endDate ORDER BY s.date ASC")
    List<Signal> findBySymbolAndDateRange(@Param("symbol") String symbol,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}