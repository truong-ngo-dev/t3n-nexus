package vn.t3nexus.identity.application.user_account.upload_avatar;

import java.io.InputStream;

public interface AvatarStorage {
    String upload(String userId, InputStream content, String contentType, long size);
    void delete(String avatarUrl);
}
