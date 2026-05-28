package vn.t3nexus.identity.domain.user;

public interface PasswordService {
    String hash(String rawPassword);
    boolean verify(String rawPassword, String hashedPassword);
}
