package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử đơn vị use case ListPendingSellerProfiles.
 */
class ListPendingSellerProfilesTest {

    private PendingSellerProfileQuery query;
    private ListPendingSellerProfiles listPendingSellerProfiles;

    @BeforeEach
    void setUp() {
        query = mock(PendingSellerProfileQuery.class);
        listPendingSellerProfiles = new ListPendingSellerProfiles(query);
    }

    @Test
    @DisplayName("ADMIN gọi (page 0, size 20) trả đúng Result mà mock trả và gọi query.find(0, 20) một lần")
    void adminCallsPageZeroSizeTwentyReturnsResult() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PendingSellerProfileQuery.Item item = new PendingSellerProfileQuery.Item(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                SellerType.INDIVIDUAL,
                "Gian Hàng Hoa Tươi",
                Instant.parse("2026-10-26T03:00:00Z")
        );
        PendingSellerProfileQuery.Result mockResult = new PendingSellerProfileQuery.Result(List.of(item), 0, 20, 1L);
        when(query.find(0, 20)).thenReturn(mockResult);

        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), 0, 20);
        ListPendingSellerProfiles.Result result = listPendingSellerProfiles.handle(q);

        assertThat(result.page()).isEqualTo(mockResult);
        verify(query).find(0, 20);
    }

    @Test
    @DisplayName("Ranh giới size 1 hợp lệ")
    void boundarySizeOneValid() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PendingSellerProfileQuery.Result mockResult = new PendingSellerProfileQuery.Result(List.of(), 0, 1, 0L);
        when(query.find(0, 1)).thenReturn(mockResult);

        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), 0, 1);
        ListPendingSellerProfiles.Result result = listPendingSellerProfiles.handle(q);

        assertThat(result.page()).isEqualTo(mockResult);
        verify(query).find(0, 1);
    }

    @Test
    @DisplayName("Ranh giới size 50 hợp lệ")
    void boundarySizeFiftyValid() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PendingSellerProfileQuery.Result mockResult = new PendingSellerProfileQuery.Result(List.of(), 0, 50, 0L);
        when(query.find(0, 50)).thenReturn(mockResult);

        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), 0, 50);
        ListPendingSellerProfiles.Result result = listPendingSellerProfiles.handle(q);

        assertThat(result.page()).isEqualTo(mockResult);
        verify(query).find(0, 50);
    }

    @Test
    @DisplayName("size 0 ném SELLER_FIELD_INVALID với field size")
    void sizeZeroThrowsSellerFieldInvalid() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), 0, 0);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("size");
                });

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("size 51 ném SELLER_FIELD_INVALID với field size")
    void sizeFiftyOneThrowsSellerFieldInvalid() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), 0, 51);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("size");
                });

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("page -1 ném SELLER_FIELD_INVALID với field page")
    void pageNegativeOneThrowsSellerFieldInvalid() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), -1, 20);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("page");
                });

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("page -1 cùng size 0 thì báo field page vì kiểm page trước")
    void pageNegativeOneAndSizeZeroReportsPageFirst() {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(admin(adminId), -1, 0);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException fe = (SellerFieldException) ex;
                    assertThat(fe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(fe.field()).isEqualTo("page");
                });

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("USER bị ném SELLER_ROLE_NOT_ALLOWED và verifyNoInteractions(query)")
    void userRoleNotAllowed() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(user(userId), 0, 20);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("Vai rỗng bị ném SELLER_ROLE_NOT_ALLOWED và verifyNoInteractions(query)")
    void emptyRoleNotAllowed() {
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ListPendingSellerProfiles.Query q = new ListPendingSellerProfiles.Query(new SellerActor(actorId, ""), 0, 20);

        assertThatThrownBy(() -> listPendingSellerProfiles.handle(q))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));

        verifyNoInteractions(query);
    }
}
