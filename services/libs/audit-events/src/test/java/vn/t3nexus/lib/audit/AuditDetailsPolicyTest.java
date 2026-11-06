package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditDetailsPolicyTest {

    @Test
    void validMapWithVariousTypesIsKeptAndDroppedIsEmpty() {
        Map<String, Object> raw = Map.of(
                "str", "hello",
                "numInt", 123,
                "numLong", 456L,
                "flag", true,
                "decimal", new BigDecimal("12.34"),
                "dbl", 3.14
        );

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.dropped()).isEmpty();
        assertThat(result.kept()).containsAllEntriesOf(raw);
    }

    @Test
    void nullAndEmptyRawMapReturnEmptyKeptAndDropped() {
        AuditDetailsPolicy.Result rNull = AuditDetailsPolicy.lenient(null);
        assertThat(rNull.kept()).isEmpty();
        assertThat(rNull.dropped()).isEmpty();

        AuditDetailsPolicy.Result rEmpty = AuditDetailsPolicy.lenient(Map.of());
        assertThat(rEmpty.kept()).isEmpty();
        assertThat(rEmpty.dropped()).isEmpty();
    }

    @Test
    void nullValueIsSilentlyIgnoredAndNotInDropped() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("validKey", null);
        raw.put("anotherKey", "ok");

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).containsOnlyKeys("anotherKey");
        assertThat(result.dropped()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "email", "phone", "phoneNumber", "address", "taxCode",
            "identityNumber", "password", "token", "accessToken"
    })
    void forbiddenNamesAreRejectedWithForbiddenNameReason(String forbiddenKey) {
        Map<String, Object> raw = Map.of(forbiddenKey, "value");

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).isEmpty();
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).key()).isEqualTo(forbiddenKey);
        assertThat(result.dropped().get(0).reason()).isEqualTo(AuditDetailsPolicy.Reason.FORBIDDEN_NAME);
    }

    @Test
    void invalidKeysAreRejectedWithBadKeyReason() {
        String longKey = "a" + "b".repeat(40); // 41 ký tự
        Map<Object, Object> raw = new HashMap<>();
        raw.put("Email", "val");
        raw.put("a-b", "val");
        raw.put("", "val");
        raw.put(longKey, "val");

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).isEmpty();
        assertThat(result.dropped()).hasSize(4);
        for (AuditDetailsPolicy.Dropped d : result.dropped()) {
            assertThat(d.reason()).isEqualTo(AuditDetailsPolicy.Reason.BAD_KEY);
        }
    }

    @Test
    void nonFlatValuesAreRejectedWithNotFlatReason() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("mapVal", Map.of("a", 1));
        raw.put("listVal", List.of("x", "y"));
        raw.put("arrVal", new int[]{1, 2});
        raw.put("objVal", new Object());
        raw.put("nanVal", Double.NaN);
        raw.put("infVal", Double.POSITIVE_INFINITY);

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).isEmpty();
        assertThat(result.dropped()).hasSize(6);
        for (AuditDetailsPolicy.Dropped d : result.dropped()) {
            assertThat(d.reason()).isEqualTo(AuditDetailsPolicy.Reason.NOT_FLAT);
        }
    }

    @Test
    void stringLengthThresholdsAreEnforced() {
        String str500 = "a".repeat(500);
        String str501 = "a".repeat(501);

        Map<String, Object> raw = Map.of(
                "keyGood", str500,
                "keyBad", str501
        );

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).containsEntry("keyGood", str500);
        assertThat(result.dropped()).hasSize(1);
        assertThat(result.dropped().get(0).key()).isEqualTo("keyBad");
        assertThat(result.dropped().get(0).reason()).isEqualTo(AuditDetailsPolicy.Reason.TOO_LONG);
    }

    @Test
    void maxKeysLimitOf10IsEnforcedAndInvalidKeysDoNotConsumeSlots() {
        Map<String, Object> raw = new HashMap<>();
        for (int i = 1; i <= 12; i++) {
            String k = String.format("k%02d", i);
            raw.put(k, "v" + i);
        }
        raw.put("badKey-1", "invalid");

        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThat(result.kept()).hasSize(10);
        for (int i = 1; i <= 10; i++) {
            assertThat(result.kept()).containsKey(String.format("k%02d", i));
        }

        assertThat(result.dropped()).extracting(AuditDetailsPolicy.Dropped::reason)
                .contains(AuditDetailsPolicy.Reason.BAD_KEY, AuditDetailsPolicy.Reason.TOO_MANY);

        List<AuditDetailsPolicy.Dropped> tooMany = result.dropped().stream()
                .filter(d -> d.reason() == AuditDetailsPolicy.Reason.TOO_MANY)
                .toList();
        assertThat(tooMany).extracting(AuditDetailsPolicy.Dropped::key).containsExactly("k11", "k12");
    }

    @Test
    void droppedKeyIsTruncatedTo60Characters() {
        String key70 = "k" + "x".repeat(69);
        AuditDetailsPolicy.Dropped dropped = new AuditDetailsPolicy.Dropped(key70, AuditDetailsPolicy.Reason.BAD_KEY);

        assertThat(dropped.key()).hasSize(60);
        assertThat(dropped.key()).isEqualTo(key70.substring(0, 60));
    }

    @Test
    void keptMapIsImmutable() {
        Map<String, Object> raw = Map.of("keyOne", "val1");
        AuditDetailsPolicy.Result result = AuditDetailsPolicy.lenient(raw);

        assertThatThrownBy(() -> result.kept().put("keyTwo", "val2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void strictThrowsInvalidAuditDetailsExceptionWithoutSecretValue() {
        Map<String, Object> raw = Map.of("email", "bi-mat-123");

        assertThatThrownBy(() -> AuditDetailsPolicy.strict(raw))
                .isInstanceOf(InvalidAuditDetailsException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("FORBIDDEN_NAME")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("bi-mat-123"));
    }

    @Test
    void strictReturnsKeptMapWhenValid() {
        Map<String, Object> raw = Map.of("itemCount", 5);
        Map<String, Object> kept = AuditDetailsPolicy.strict(raw);

        assertThat(kept).containsEntry("itemCount", 5);
    }
}
