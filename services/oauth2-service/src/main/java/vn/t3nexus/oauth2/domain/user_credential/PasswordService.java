package vn.t3nexus.oauth2.domain.user_credential;

public interface PasswordService {
    String hash(String rawPassword);
    boolean verify(String rawPassword, String hashedPassword);
}
