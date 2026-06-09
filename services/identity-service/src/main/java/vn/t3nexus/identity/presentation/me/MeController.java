package vn.t3nexus.identity.presentation.me;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.identity.application.user_account.get_user_profile.GetUserProfile;
import vn.t3nexus.identity.application.user_account.update_user_profile.UpdateUserProfile;
import vn.t3nexus.identity.application.user_account.upload_avatar.UploadAvatar;
import vn.t3nexus.lib.web.commons.response.ApiResponse;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long        MAX_AVATAR_SIZE      = 5 * 1024 * 1024L;

    private final GetUserProfile    getUserProfile;
    private final UpdateUserProfile updateUserProfile;
    private final UploadAvatar      uploadAvatar;

    @GetMapping
    public ApiResponse<GetUserProfile.Result> getProfile(Authentication authentication) {
        return ApiResponse.ok(getUserProfile.handle(new GetUserProfile.Query(authentication.getName())));
    }

    @PutMapping
    public ApiResponse<UpdateUserProfile.Result> updateProfile(
            Authentication authentication,
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        UpdateUserProfile.Command command = new UpdateUserProfile.Command(
                authentication.getName(),
                request.fullName(),
                request.phoneNumber()
        );
        return ApiResponse.ok(updateUserProfile.handle(command));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadAvatar.Result> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        UploadAvatar.Command command = new UploadAvatar.Command(
                authentication.getName(),
                file.getInputStream(),
                file.getContentType(),
                file.getSize()
        );
        return ApiResponse.ok(uploadAvatar.handle(command));
    }

    public record UpdateProfileRequest(
            @NotBlank String fullName,
            @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must be 10 digits starting with 0")
            String phoneNumber
    ) {}
}
