package com.tradeback.repository;

import com.tradeback.model.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    // Основные используемые методы
    List<MarketData> findBySymbolOrderByDateAsc(String symbol);

    List<MarketData> findTopBySymbolOrderByDateDesc(String symbol);

    long countBySymbol(String symbol);

    // Универсальный метод для поиска по датам (заменяет 4 старых метода)
    @Query("SELECT m FROM MarketData m WHERE m.symbol = :symbol " +
            "AND m.date BETWEEN :startDate AND :endDate ORDER BY m.date ASC")
    List<MarketData> findBySymbolAndDateRange(@Param("symbol") String symbol,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    // Метод для получения одной записи по дате
    @Query("SELECT m FROM MarketData m WHERE m.symbol = :symbol " +
            "AND DATE(m.date) = :date ORDER BY m.date ASC LIMIT 1")
    MarketData findFirstBySymbolAndDate(@Param("symbol") String symbol,
                                        @Param("date") LocalDate date);
}