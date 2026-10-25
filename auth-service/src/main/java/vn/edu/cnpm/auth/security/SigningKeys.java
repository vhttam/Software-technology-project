package vn.edu.cnpm.auth.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.HexFormat;

@Component
public class SigningKeys {
    private final RSAPrivateKey privateKey;
    private final JWKSet jwks;
    private final String keyId;

    public SigningKeys(Environment environment) {
        try {
            String configured = environment.getProperty("JWT_PRIVATE_KEY_BASE64", "").trim();
            String pathSetting = environment.getProperty("JWT_PRIVATE_KEY_PATH", "").trim();
            RSAPrivateKey selected;
            if (!configured.isBlank()) selected = decode(configured);
            else if (!pathSetting.isBlank()) selected = readOrCreate(Path.of(pathSetting));
            else selected = generate().getPrivate() instanceof RSAPrivateKey rsa ? rsa : null;
            if (selected == null) throw new IllegalStateException("RSA signing key could not be created");
            privateKey = selected;
            RSAPublicKey publicKey = derivePublic(selected);
            String configuredKeyId = environment.getProperty("JWT_KEY_ID", "").trim();
            keyId = configuredKeyId.isBlank() ? stableKeyId(publicKey) : configuredKeyId;
            jwks = new JWKSet(new RSAKey.Builder(publicKey)
                    .keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo khóa ký JWT", ex);
        }
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public String keyId() { return keyId; }
    public Object jwks() { return jwks.toJSONObject(); }

    private RSAPrivateKey readOrCreate(Path configuredPath) throws Exception {
        Path path = configuredPath.toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (Files.exists(path)) return decodeBytes(Files.readAllBytes(path));
        byte[] encoded = generate().getPrivate().getEncoded();
        try {
            Files.write(path, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictPermissions(path);
            return decodeBytes(encoded);
        } catch (java.nio.file.FileAlreadyExistsException race) {
            return decodeBytes(Files.readAllBytes(path));
        }
    }

    private RSAPrivateKey decode(String value) throws Exception {
        return decodeBytes(java.util.Base64.getDecoder().decode(value));
    }

    private RSAPrivateKey decodeBytes(byte[] encoded) throws Exception {
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private KeyPair generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private RSAPublicKey derivePublic(RSAPrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("RSA signing key must include CRT parameters to derive its public key");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private String stableKeyId(RSAPublicKey publicKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
        return "key-" + HexFormat.of().formatHex(digest, 0, 8);
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // Windows uses the inherited ACL of the user-private .local-data directory.
        }
    }
}
