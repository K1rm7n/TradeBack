package com.tradeback.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_history", indexes = {
        @Index(name = "idx_user_history_user_id", columnList = "user_id"),
        @Index(name = "idx_user_history_symbol", columnList = "symbol"),
        @Index(name = "idx_user_history_request_time", columnList = "request_time"),
        @Index(name = "idx_user_history_user_request_time", columnList = "user_id, request_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(length = 10)
    private String firstIndicatorType;

    private Integer firstPeriod;

    @Column(length = 10)
    private String secondIndicatorType;

    private Integer secondPeriod;

    @Column(length = 10)
    private String thirdIndicatorType;

    private Integer thirdPeriod;

    @Column(name = "time_interval", length = 10) // ИСПРАВЛЕНО: используем то же название, что и в Indicator
    private String interval;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime requestTime;

    @Column(columnDefinition = "TEXT")
    private String aiAdvice;
}