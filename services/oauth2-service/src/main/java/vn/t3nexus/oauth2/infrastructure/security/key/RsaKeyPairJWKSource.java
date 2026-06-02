package vn.t3nexus.oauth2.infrastructure.security.key;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RsaKeyPairJWKSource implements JWKSource<SecurityContext> {

    private final RsaKeyPairRepository keyPairRepository;

    public RsaKeyPairJWKSource(RsaKeyPairRepository keyPairRepository) {
        this.keyPairRepository = keyPairRepository;
    }

    @Override
    public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
        List<RsaKeyPairRepository.RsaKeyPair> keyPairs = this.keyPairRepository.findKeyPairs();
        List<JWK> result = new ArrayList<>(keyPairs.size());
        for (RsaKeyPairRepository.RsaKeyPair keyPair : keyPairs) {
            RSAKey rsaKey = new RSAKey.Builder(keyPair.publicKey())
                    .privateKey(keyPair.privateKey())
                    .keyID(keyPair.id())
                    .build();
            if (jwkSelector.getMatcher().matches(rsaKey)) {
                result.add(rsaKey);
            }
        }
        return result;
    }
}
