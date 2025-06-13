package com.tradeback.web;

import com.tradeback.config.ApplicationConstants;
import com.tradeback.dto.LoginRequest;
import com.tradeback.dto.RegistrationRequest;
import com.tradeback.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        if (session.getAttribute(ApplicationConstants.USER_SESSION_KEY) != null) {
            return "redirect:/";
        }
        model.addAttribute("title", "Login");
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model, HttpSession session) {
        if (session.getAttribute(ApplicationConstants.USER_SESSION_KEY) != null) {
            return "redirect:/";
        }
        model.addAttribute("title", "Sign Up");
        model.addAttribute("registrationRequest", new RegistrationRequest());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute RegistrationRequest request,
                                BindingResult bindingResult,
                                HttpSession session, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("title", "Sign Up");
            return "auth/signup";
        }

        try {
            userService.register(request.getUsername(), request.getPassword(), request.getEmail());
            session.setAttribute(ApplicationConstants.USER_SESSION_KEY, request.getUsername());
            return "redirect:/";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("title", "Sign Up");
            return "auth/signup";
        }
    }

    @PostMapping("/authenticate")
    public String authenticate(@Valid @ModelAttribute LoginRequest request,
                               BindingResult bindingResult,
                               HttpSession session, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("title", "Login");
            return "auth/login";
        }

        if (userService.authenticate(request.getUsername(), request.getPassword())) {
            session.setAttribute(ApplicationConstants.USER_SESSION_KEY, request.getUsername());
            return "redirect:/";
        } else {
            model.addAttribute("error", "Invalid username or password");
            model.addAttribute("title", "Login");
            return "auth/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}