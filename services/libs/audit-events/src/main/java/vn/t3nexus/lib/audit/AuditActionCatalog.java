package vn.t3nexus.lib.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Danh mục quản lý tất cả các hành động kiểm toán được khai báo trong dịch vụ.
 */
public final class AuditActionCatalog {

    private final Map<String, AuditActionDeclaration> declarations;

    private AuditActionCatalog(Map<String, AuditActionDeclaration> declarations) {
        this.declarations = Map.copyOf(declarations);
    }

    public static AuditActionCatalog of(AuditActionDeclaration... declarations) {
        Map<String, AuditActionDeclaration> map = new HashMap<>();
        if (declarations != null) {
            for (AuditActionDeclaration decl : declarations) {
                if (decl == null) {
                    continue;
                }
                if (map.containsKey(decl.action())) {
                    throw new IllegalArgumentException(decl.action());
                }
                map.put(decl.action(), decl);
            }
        }
        return new AuditActionCatalog(map);
    }

    public Optional<AuditActionDeclaration> find(String action) {
        return Optional.ofNullable(declarations.get(action));
    }
}
