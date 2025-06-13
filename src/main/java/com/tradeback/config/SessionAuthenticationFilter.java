package com.tradeback.config;

import com.tradeback.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Пропускаем API endpoints - они используют JWT
        if (path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Пропускаем публичные страницы
        if (isPublicPage(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String username = (String) session.getAttribute(ApplicationConstants.USER_SESSION_KEY);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Аутентифицируем пользователя на основе сессии
                    userService.findByUsername(username).ifPresent(user -> {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        user,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                );
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("Authenticated user from session: {}", username);
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Session authentication failed: {}", e.getMessage());
            // Не пробрасываем исключение, просто логируем
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Проверяет, является ли страница публичной (не требует аутентификации)
     */
    private boolean isPublicPage(String path) {
        // Главная страница
        if (path.equals("/")) return true;

        // Страницы аутентификации
        if (path.equals("/login")) return true;
        if (path.equals("/signup")) return true;
        if (path.equals("/authenticate")) return true;
        if (path.equals("/logout")) return true;

        // Статические ресурсы
        if (path.startsWith("/css/")) return true;
        if (path.startsWith("/js/")) return true;
        if (path.startsWith("/images/")) return true;
        if (path.startsWith("/static/")) return true;
        if (path.equals("/favicon.ico")) return true;

        // Actuator endpoints (если нужны)
        if (path.startsWith("/actuator/health")) return true;

        return false;
    }

    /**
     * Проверяет, требует ли страница аутентификации
     */
    private boolean requiresAuthentication(String path) {
        // Защищенные веб-страницы
        return path.startsWith("/history") ||
                path.startsWith("/signals") ||
                path.startsWith("/indicators") ||
                path.startsWith("/profile");
    }
}
