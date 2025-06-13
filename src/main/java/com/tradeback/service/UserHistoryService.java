package com.tradeback.service;

import com.tradeback.dto.IndicatorRequest;
import com.tradeback.model.User;
import com.tradeback.model.UserHistory;
import com.tradeback.repository.UserHistoryRepository;
import com.tradeback.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserHistoryService {

    private final UserHistoryRepository userHistoryRepository;
    private final UserRepository userRepository;

    public UserHistory saveRequest(String username, IndicatorRequest request, String aiAdvice) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    UserHistory history = createUserHistory(user, request, aiAdvice);
                    return userHistoryRepository.save(history);
                })
                .orElse(null);
    }

    public List<UserHistory> getUserHistory(String username) {
        return userRepository.findByUsername(username)
                .map(user -> userHistoryRepository.findByUserIdOrderByRequestTimeDesc(user.getId()))
                .orElse(List.of());
    }

    public List<UserHistory> getRecentUserHistory(String username, int limit) {
        return userRepository.findByUsername(username)
                .map(user -> userHistoryRepository.findTopNByUserIdOrderByRequestTimeDesc(user.getId(), limit))
                .orElse(List.of());
    }

    public Optional<UserHistory> getHistoryById(Long id) {
        return userHistoryRepository.findById(id);
    }

    public void deleteHistory(Long id) {
        userHistoryRepository.deleteById(id);
    }

    // Приватный вспомогательный метод
    private UserHistory createUserHistory(User user, IndicatorRequest request, String aiAdvice) {
        UserHistory history = new UserHistory();
        history.setUser(user);
        history.setSymbol(request.getSymbol());
        history.setFirstIndicatorType(request.getFirstIndicatorType());
        history.setFirstPeriod(request.getFirstPeriod());
        history.setSecondIndicatorType(request.getSecondIndicatorType());
        history.setSecondPeriod(request.getSecondPeriod());
        history.setThirdIndicatorType(request.getThirdIndicatorType());
        history.setThirdPeriod(request.getThirdPeriod());
        history.setInterval(request.getInterval());
        history.setRequestTime(LocalDateTime.now());
        history.setAiAdvice(aiAdvice);
        return history;
    }
}