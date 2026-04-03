package com.kindred.emkcrm_project_backend.authentication.impl;

import com.kindred.emkcrm_project_backend.api.UsersApiDelegate;
import com.kindred.emkcrm_project_backend.authentication.CurrentUserProfileService;
import com.kindred.emkcrm_project_backend.model.UpdateCurrentUserRequest;
import com.kindred.emkcrm_project_backend.model.UserProfileDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class UsersApiDelegateImpl implements UsersApiDelegate {

    private final CurrentUserProfileService currentUserProfileService;

    public UsersApiDelegateImpl(CurrentUserProfileService currentUserProfileService) {
        this.currentUserProfileService = currentUserProfileService;
    }

    @Override
    public ResponseEntity<UserProfileDto> getCurrentUserProfile() {
        return new ResponseEntity<>(currentUserProfileService.getCurrentUserProfile(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<UserProfileDto> updateCurrentUserProfile(UpdateCurrentUserRequest request) {
        return new ResponseEntity<>(currentUserProfileService.updateCurrentUserProfile(request), HttpStatus.OK);
    }
}
