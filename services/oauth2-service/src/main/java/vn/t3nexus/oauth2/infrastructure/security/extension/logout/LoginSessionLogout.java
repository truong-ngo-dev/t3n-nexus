package vn.t3nexus.oauth2.infrastructure.security.extension.logout;

import org.springframework.stereotype.Component;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.transaction.support.TransactionOperations;
import vn.t3nexus.oauth2.application.login_session.end_login_session.EndLoginSession;

/**
 * Phần có giao dịch của đăng xuất: <b>một giao dịch</b> bao việc kết thúc phiên của ta ({@link EndLoginSession}: xóa phiên người
 * dùng, kéo theo phiên ứng dụng) và việc xóa các bản ghi ủy quyền của từng phiên ứng dụng (mã làm mới nằm ở đó). Lỗi ở bất kỳ bước
 * nào quay lui cả hai, nên không có trạng thái nửa vời: phiên mất mà bản ghi còn, hoặc bản ghi mất mà phiên còn (ADR-0003 mục 9, cùng
 * cách với điểm móc lúc cấp token).
 *
 * <p>Báo ứng dụng giữ mã (BFF) và hủy session Spring <b>không</b> nằm ở đây: bên gọi làm sau khi hàm này trả về, tức sau khi giao
 * dịch đã commit, để giao dịch hỏng thì chưa động tới gì.
 *
 * <p>Việc phiên được khóa hàng khi nạp ({@code findActiveByIdpSessionIdForUpdate}) làm hai yêu cầu đăng xuất đồng thời nối tiếp nhau:
 * bên sau thấy phiên đã mất và trả kết quả rỗng (INV-ATH-05).
 */
@Component
public class LoginSessionLogout {

    private final EndLoginSession            endLoginSession;
    private final OAuth2AuthorizationService authorizationService;
    private final TransactionOperations      transactions;

    public LoginSessionLogout(EndLoginSession endLoginSession, OAuth2AuthorizationService authorizationService,
                              TransactionOperations transactions) {
        this.endLoginSession      = endLoginSession;
        this.authorizationService = authorizationService;
        this.transactions         = transactions;
    }

    /** @return các phiên ứng dụng vừa kết thúc (rỗng nếu không có phiên nào bị kết thúc) */
    public EndLoginSession.Result end(String idpSessionId, String hintSid) {
        return transactions.execute(status -> {
            EndLoginSession.Result result = endLoginSession.handle(new EndLoginSession.Command(idpSessionId, hintSid));
            for (EndLoginSession.EndedClientSession ended : result.endedClientSessions()) {
                OAuth2Authorization authorization = authorizationService.findById(ended.authorizationId());
                if (authorization != null) authorizationService.remove(authorization);
            }
            return result;
        });
    }
}
