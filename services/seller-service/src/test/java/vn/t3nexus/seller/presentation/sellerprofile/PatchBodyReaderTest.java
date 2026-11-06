package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.seller.application.sellerprofile.Patch;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerFieldException;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử đơn vị cho PatchBodyReader.
 */
class PatchBodyReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("requireObject với object trả chính nó, với mảng/chuỗi/null thì ném SellerFieldException")
    void testRequireObject() {
        JsonNode obj = read("{\"a\": 1}");
        assertThat(PatchBodyReader.requireObject(obj)).isSameAs(obj);

        JsonNode arr = read("[1, 2]");
        assertThatThrownBy(() -> PatchBodyReader.requireObject(arr))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("body");
                });

        JsonNode str = read("\"hello\"");
        assertThatThrownBy(() -> PatchBodyReader.requireObject(str))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("body");
                });

        assertThatThrownBy(() -> PatchBodyReader.requireObject(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("body");
                });
    }

    @Test
    @DisplayName("objectOrNull: khóa vắng thì null, object thì trả nút, null JSON hoặc chuỗi thì ném SellerFieldException")
    void testObjectOrNull() {
        JsonNode root = read("{\"obj\": {\"k\": \"v\"}, \"nullKey\": null, \"strKey\": \"abc\"}");

        assertThat(PatchBodyReader.objectOrNull(root, "absentKey")).isNull();
        assertThat(PatchBodyReader.objectOrNull(null, "absentKey")).isNull();

        JsonNode objNode = PatchBodyReader.objectOrNull(root, "obj");
        assertThat(objNode).isNotNull();
        assertThat(objNode.isObject()).isTrue();

        assertThatThrownBy(() -> PatchBodyReader.objectOrNull(root, "nullKey"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("nullKey");
                });

        assertThatThrownBy(() -> PatchBodyReader.objectOrNull(root, "strKey"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("strKey");
                });
    }

    @Test
    @DisplayName("text: absent, null, string và các kiểu sai định dạng")
    void testText() {
        JsonNode root = read("{\"str\": \"abc\", \"nullVal\": null, \"num\": 123, \"b\": true, \"obj\": {}, \"arr\": []}");

        assertThat(PatchBodyReader.text(root, "absent").isAbsent()).isTrue();
        assertThat(PatchBodyReader.text(null, "absent").isAbsent()).isTrue();

        Patch<String> nullPatch = PatchBodyReader.text(root, "nullVal");
        assertThat(nullPatch.present()).isTrue();
        assertThat(nullPatch.value()).isNull();

        Patch<String> strPatch = PatchBodyReader.text(root, "str");
        assertThat(strPatch).isEqualTo(Patch.of("abc"));

        // Các kiểu sai: num, b, obj, arr
        for (String field : new String[]{"num", "b", "obj", "arr"}) {
            assertThatThrownBy(() -> PatchBodyReader.text(root, field))
                    .isInstanceOf(SellerFieldException.class)
                    .satisfies(ex -> {
                        SellerFieldException sfe = (SellerFieldException) ex;
                        assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                        assertThat(sfe.field()).isEqualTo(field);
                        assertThat(sfe.getMessage()).doesNotContain("123");
                    });
        }
    }

    @Test
    @DisplayName("sellerType: absent, BUSINESS, các giá trị lỗi")
    void testSellerType() {
        JsonNode root = read("{\"valid\": \"BUSINESS\", \"lowercase\": \"business\", \"company\": \"COMPANY\", \"nullVal\": null, \"num\": 5}");

        assertThat(PatchBodyReader.sellerType(root, "absent").isAbsent()).isTrue();
        assertThat(PatchBodyReader.sellerType(null, "absent").isAbsent()).isTrue();

        assertThat(PatchBodyReader.sellerType(root, "valid")).isEqualTo(Patch.of(SellerType.BUSINESS));

        for (String field : new String[]{"lowercase", "company", "nullVal", "num"}) {
            assertThatThrownBy(() -> PatchBodyReader.sellerType(root, field))
                    .isInstanceOf(SellerFieldException.class)
                    .satisfies(ex -> {
                        SellerFieldException sfe = (SellerFieldException) ex;
                        assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                        assertThat(sfe.field()).isEqualTo(field);
                    });
        }
    }

    @Test
    @DisplayName("parseSellerType: hợp lệ, null hoặc chuỗi lạ")
    void testParseSellerType() {
        assertThat(PatchBodyReader.parseSellerType("HOUSEHOLD")).isEqualTo(SellerType.HOUSEHOLD);
        assertThat(PatchBodyReader.parseSellerType("INDIVIDUAL")).isEqualTo(SellerType.INDIVIDUAL);
        assertThat(PatchBodyReader.parseSellerType("BUSINESS")).isEqualTo(SellerType.BUSINESS);

        assertThatThrownBy(() -> PatchBodyReader.parseSellerType(null))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("sellerType");
                });

        assertThatThrownBy(() -> PatchBodyReader.parseSellerType("X"))
                .isInstanceOf(SellerFieldException.class)
                .satisfies(ex -> {
                    SellerFieldException sfe = (SellerFieldException) ex;
                    assertThat(sfe.getErrorCode()).isEqualTo(SellerErrorCode.SELLER_FIELD_INVALID);
                    assertThat(sfe.field()).isEqualTo("sellerType");
                });
    }
}
