package com.tradeback.repository;

import com.tradeback.model.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorRepository extends JpaRepository<Indicator, Long> {

    // Поиск по символу и типу индикатора
    List<Indicator> findBySymbolAndType(String symbol, Indicator.IndicatorType type);

    // ИСПРАВЛЕНО: используем правильное поле calculatedAt вместо date
    @Query("SELECT i FROM Indicator i WHERE i.symbol = :symbol AND i.type = :type " +
            "AND i.period = :period ORDER BY i.calculatedAt DESC LIMIT 1")
    Optional<Indicator> findLatestBySymbolTypeAndPeriod(@Param("symbol") String symbol,
                                                        @Param("type") Indicator.IndicatorType type,
                                                        @Param("period") int period);

    // Дополнительные методы для поиска по символу
    List<Indicator> findBySymbolOrderByCalculatedAtDesc(String symbol);

    // Поиск по символу и периоду
    List<Indicator> findBySymbolAndPeriodOrderByCalculatedAtDesc(String symbol, int period);
}