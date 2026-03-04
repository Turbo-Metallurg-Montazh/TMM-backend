package com.kindred.emkcrm_project_backend.authentication.impl;

import com.kindred.emkcrm_project_backend.api.AdminUsersApiDelegate;
import com.kindred.emkcrm_project_backend.authentication.rbac.AdminUserManagementService;
import com.kindred.emkcrm_project_backend.model.AdminCreateUserRequest;
import com.kindred.emkcrm_project_backend.model.AdminUserDto;
import com.kindred.emkcrm_project_backend.model.MessageResponse;
import com.kindred.emkcrm_project_backend.model.UpdateUserEnabledRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUsersApiDelegateImpl implements AdminUsersApiDelegate {

    private final AdminUserManagementService adminUserManagementService;

    public AdminUsersApiDelegateImpl(AdminUserManagementService adminUserManagementService) {
        this.adminUserManagementService = adminUserManagementService;
    }

    @Override
    public ResponseEntity<List<AdminUserDto>> listUsers() {
        return new ResponseEntity<>(adminUserManagementService.listUsers(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<AdminUserDto> getUserByUsername(String username) {
        return new ResponseEntity<>(adminUserManagementService.getUserByUsername(username), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<AdminUserDto> createUser(AdminCreateUserRequest request) {
        return new ResponseEntity<>(adminUserManagementService.createUser(request), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<MessageResponse> deleteUserByUsername(String username) {
        return new ResponseEntity<>(adminUserManagementService.deleteUserByUsername(username), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MessageResponse> resetUserPassword(String username) {
        return new ResponseEntity<>(adminUserManagementService.resetUserPassword(username), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<AdminUserDto> updateUserEnabled(String username, UpdateUserEnabledRequest request) {
        return new ResponseEntity<>(adminUserManagementService.updateUserEnabled(username, request), HttpStatus.OK);
    }
}
