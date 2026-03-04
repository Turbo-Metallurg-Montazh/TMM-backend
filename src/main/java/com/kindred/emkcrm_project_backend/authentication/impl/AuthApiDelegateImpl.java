package com.kindred.emkcrm_project_backend.authentication.impl;

import com.kindred.emkcrm_project_backend.api.AuthApiDelegate;
import com.kindred.emkcrm_project_backend.authentication.PasswordResetService;
import com.kindred.emkcrm_project_backend.model.*;
import com.kindred.emkcrm_project_backend.authentication.JwtTokenProvider;
import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.db.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public AuthApiDelegateImpl(
            JwtTokenProvider jwtTokenProvider,
            UserService userService,
            PasswordResetService passwordResetService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.passwordResetService = passwordResetService;
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest loginRequest) {
        User user = userService.validateUsername(loginRequest);
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken(jwtTokenProvider.generateToken(user.getUsername()));
        return new ResponseEntity<>(tokenResponse, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MessageResponse> confirmPasswordReset(PasswordResetConfirmRequest passwordResetConfirmRequest) {
        passwordResetService.confirmPasswordReset(passwordResetConfirmRequest.getToken(), passwordResetConfirmRequest.getNewPassword());
        MessageResponse response = new MessageResponse();
        response.setMessage("Пароль успешно обновлен");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
