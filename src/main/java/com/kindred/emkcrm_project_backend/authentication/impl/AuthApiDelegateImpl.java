package com.kindred.emkcrm_project_backend.authentication.impl;

import com.kindred.emkcrm_project_backend.api.AuthApiDelegate;
import com.kindred.emkcrm_project_backend.authentication.PasswordResetService;
import com.kindred.emkcrm_project_backend.authentication.RefreshTokenService;
import com.kindred.emkcrm_project_backend.model.*;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.db.entities.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;

    public AuthApiDelegateImpl(
            UserService userService,
            PasswordResetService passwordResetService,
            RefreshTokenService refreshTokenService
    ) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public ResponseEntity<MessageResponse> login(LoginRequest loginRequest) {
        User user = userService.validateUsername(loginRequest);
        refreshTokenService.createSession(user, currentResponse());
        return message("Logged in", HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MessageResponse> refresh() {
        refreshTokenService.refreshSession(currentRequest(), currentResponse());
        return message("Session refreshed", HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MessageResponse> logout() {
        refreshTokenService.logout(currentRequest(), currentResponse());
        return message("Logged out", HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MessageResponse> confirmPasswordReset(PasswordResetConfirmRequest passwordResetConfirmRequest) {
        passwordResetService.confirmPasswordReset(passwordResetConfirmRequest.getToken(), passwordResetConfirmRequest.getNewPassword());
        return message("Пароль успешно обновлен", HttpStatus.OK);
    }

    private ResponseEntity<MessageResponse> message(String text, HttpStatus status) {
        MessageResponse response = new MessageResponse();
        response.setMessage(text);
        return new ResponseEntity<>(response, status);
    }

    private HttpServletRequest currentRequest() {
        return currentAttributes().getRequest();
    }

    private HttpServletResponse currentResponse() {
        HttpServletResponse response = currentAttributes().getResponse();
        if (response == null) {
            throw new IllegalStateException("No current HTTP response");
        }
        return response;
    }

    private ServletRequestAttributes currentAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }

}
