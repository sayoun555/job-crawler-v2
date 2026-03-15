package com.portfolio.jobcrawler.application.auth.dto;

public record SignupCommand(String email, String password, String nickname) {
}
