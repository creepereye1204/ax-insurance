package com.ax.core.customer;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 주민등록번호 처리. 공개 데모이므로 <b>실제 번호는 받지 않고 시스템이 만든 가상 번호만</b> 저장한다.
 *
 * <ul>
 *   <li>가상 번호: 생년월일·성별은 맞추되 뒷자리 2~3번째를 "99"로 두고 검증번호를 일부러 틀리게 만든다 → 실존 번호와 겹치지 않는다</li>
 *   <li>저장: AES-256-GCM 암호문 (원문은 DB 에 없다)</li>
 *   <li>검색·중복 확인: HMAC-SHA256 블라인드 인덱스 (암호문은 매번 달라 비교할 수 없으므로)</li>
 *   <li>표시: 마스킹(앞 6자리 + 성별 자리). 원문 열람은 사유와 함께 감사 로그를 남긴다</li>
 * </ul>
 * 키는 환경변수 AX_RRN_KEY (base64 32바이트). 없으면 개발용 고정 키를 쓰고 경고한다.
 */
@Slf4j
@Component
public class ResidentNumbers {

    private static final String DEV_KEY_SEED = "ax-insurance-dev-only-rrn-key";
    private static final int[] WEIGHTS = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Protected(String encrypted, String masked, String hash) {
    }

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;

    public ResidentNumbers(@Value("${ax.rrn.key:}") String base64Key) {
        byte[] key;
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("AX_RRN_KEY 가 없어 개발용 고정 키로 주민번호를 암호화합니다. 운영에서는 반드시 설정하세요.");
            key = sha256(DEV_KEY_SEED.getBytes(StandardCharsets.UTF_8));
        } else {
            key = Base64.getDecoder().decode(base64Key.trim());
            if (key.length != 32) {
                throw new IllegalStateException("AX_RRN_KEY 는 base64 로 인코딩한 32바이트여야 합니다.");
            }
        }
        this.aesKey = new SecretKeySpec(key, "AES");
        // 암호화 키와 인덱스 키를 분리 (같은 키를 두 용도로 쓰지 않는다)
        this.hmacKey = new SecretKeySpec(hmac(new SecretKeySpec(key, "HmacSHA256"), "rrn-blind-index"), "HmacSHA256");
    }

    /** 생년월일·성별에 맞는 가상 주민번호 (yyMMdd-GXXXXXX) */
    public static String generateVirtual(LocalDate birthDate, Gender gender) {
        boolean y2k = birthDate.getYear() >= 2000;
        int g = gender == Gender.M ? (y2k ? 3 : 1) : (y2k ? 4 : 2);
        String twelve = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + g + "99" + "%03d".formatted(RANDOM.nextInt(1000));
        return twelve.substring(0, 6) + "-" + twelve.substring(6) + (checkDigit(twelve) + 1) % 10;
    }

    /** 이 시스템이 만든 가상 번호인가 (뒷자리 "G99", 검증번호 불일치) */
    public static boolean isVirtual(String rrn) {
        String d = rrn.replace("-", "");
        return d.length() == 13 && d.chars().allMatch(Character::isDigit) && d.startsWith("99", 7)
                && checkDigit(d.substring(0, 12)) != d.charAt(12) - '0';
    }

    public static String mask(String rrn) {
        return rrn.substring(0, 8) + "******";
    }

    public Protected protect(String rrn) {
        if (!isVirtual(rrn)) {
            throw new IllegalArgumentException("가상 주민번호만 저장할 수 있습니다.");
        }
        return new Protected(encrypt(rrn), mask(rrn), blindIndex(rrn));
    }

    public String blindIndex(String rrn) {
        return HexFormat.of().formatHex(hmac(hmacKey, rrn.replace("-", "")));
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return "v1:" + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("주민번호 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring("v1:".length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, in, 0, 12));
            return new String(cipher.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("주민번호 복호화 실패 (키가 바뀌었거나 데이터가 손상됨)", e);
        }
    }

    static int checkDigit(String twelve) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (twelve.charAt(i) - '0') * WEIGHTS[i];
        }
        return (11 - sum % 11) % 10;
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
