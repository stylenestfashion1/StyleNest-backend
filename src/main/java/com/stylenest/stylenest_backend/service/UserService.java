package com.stylenest.stylenest_backend.service;

import com.stylenest.stylenest_backend.dto.user.UpdateProfileRequest;
import com.stylenest.stylenest_backend.dto.user.UserProfileResponse;

public interface UserService {

    UserProfileResponse getProfile();

    UserProfileResponse updateProfile(UpdateProfileRequest request);
}