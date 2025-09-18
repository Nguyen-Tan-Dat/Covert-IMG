package org.dat;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import java.security.MessageDigest;

public class License {
    // ====== Win constants ======
    static final int PROV_RSA_AES = 24;
    static final int CALG_MD5 = 0x00008003;
    static final int CALG_AES_256 = 0x00006610;
    static final int CRYPT_VERIFYCONTEXT = 0xF0000000; // (không dùng ở đây)
    static final int HP_HASHVAL = 0x0002;              // (không dùng ở đây)

    public interface Advapi32 extends Library {
        Advapi32 INSTANCE = Native.load("Advapi32", Advapi32.class);

        boolean CryptAcquireContextA(PointerByReference phProv, String pszContainer, String pszProvider,
                                     int dwProvType, int dwFlags);
        boolean CryptReleaseContext(Pointer hProv, int dwFlags);

        boolean CryptCreateHash(Pointer hProv, int Algid, Pointer hKey, int dwFlags,
                                PointerByReference phHash);
        boolean CryptHashData(Pointer hHash, byte[] pbData, int dataLen, int flags);

        boolean CryptDeriveKey(Pointer hProv, int Algid, Pointer hBaseData, int dwFlags,
                               PointerByReference phKey);

        boolean CryptEncrypt(Pointer hKey, Pointer hHash, boolean Final, int dwFlags,
                             byte[] pbData, IntByReference pdwDataLen, int dwBufLen);

        boolean CryptGetHashParam(Pointer hHash, int dwParam, byte[] pbData,
                                  IntByReference pdwDataLen, int dwFlags);

        boolean CryptDestroyHash(Pointer hHash);
        boolean CryptDestroyKey(Pointer hKey);
    }

    private static byte[] cryptoapiAesEncryptHexBytes(String data, String key) {
        PointerByReference phProvRef = new PointerByReference();
        if (!Advapi32.INSTANCE.CryptAcquireContextA(phProvRef, null, null, PROV_RSA_AES, 0)) {
            throw new RuntimeException("CryptAcquireContext failed");
        }
        Pointer hProv = phProvRef.getValue();

        try {
            // MD5(key) -> Derive AES-256
            PointerByReference phHashRef = new PointerByReference();
            if (!Advapi32.INSTANCE.CryptCreateHash(hProv, CALG_MD5, null, 0, phHashRef)) {
                throw new RuntimeException("CryptCreateHash failed");
            }
            Pointer hHash = phHashRef.getValue();
            try {
                byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (!Advapi32.INSTANCE.CryptHashData(hHash, keyBytes, keyBytes.length, 0)) {
                    throw new RuntimeException("CryptHashData failed");
                }

                PointerByReference phKeyRef = new PointerByReference();
                if (!Advapi32.INSTANCE.CryptDeriveKey(hProv, CALG_AES_256, hHash, 0, phKeyRef)) {
                    throw new RuntimeException("CryptDeriveKey failed");
                }
                Pointer hKey = phKeyRef.getValue();
                try {
                    byte[] input = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    // Lần 1: hỏi size sau padding
                    IntByReference sizeRef = new IntByReference(input.length);
                    if (!Advapi32.INSTANCE.CryptEncrypt(hKey, null, true, 0, null, sizeRef, input.length)) {
                        throw new RuntimeException("CryptEncrypt (size query) failed");
                    }
                    int outSize = sizeRef.getValue();

                    // Lần 2: mã hoá
                    byte[] buf = new byte[outSize];
                    System.arraycopy(input, 0, buf, 0, input.length);
                    IntByReference outLenRef = new IntByReference(input.length);
                    if (!Advapi32.INSTANCE.CryptEncrypt(hKey, null, true, 0, buf, outLenRef, buf.length)) {
                        throw new RuntimeException("CryptEncrypt failed");
                    }
                    int encLen = outLenRef.getValue();

                    // Xuất hex ASCII
                    StringBuilder sb = new StringBuilder(encLen * 2);
                    for (int i = 0; i < encLen; i++) {
                        sb.append(String.format("%02x", buf[i] & 0xFF));
                    }
                    return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                } finally {
                    Advapi32.INSTANCE.CryptDestroyKey(hKey);
                }
            } finally {
                Advapi32.INSTANCE.CryptDestroyHash(hHash);
            }
        } finally {
            Advapi32.INSTANCE.CryptReleaseContext(hProv, 0);
        }
    }

    static String buildRawInfo() throws Exception {
        // VOLSN từ ổ C:
        String vol = runAndRead("cmd.exe", "/c", "wmic", "volume", "where", "driveletter='C:'", "get", "serialnumber");
        String volSn = vol.lines()
                .filter(s -> s.trim().matches("[0-9A-Fa-f]+"))
                .findFirst()
                .orElse("0")
                .trim();

        // CPU Name từ registry
        String cpu = runAndRead("reg", "query",
                "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                "/v", "ProcessorNameString");

        String cpuName = cpu.lines()
                .filter(s -> s.contains("ProcessorNameString"))
                .map(s -> s.replaceAll(".*ProcessorNameString\\s+REG_SZ\\s+", "").trim())
                .findFirst()
                .orElse("UNKNOWN");

        return "VOLSN:" + volSn + ";" + "CPU:" + cpuName + ";";
    }


    static String runAndRead(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (java.io.InputStream is = p.getInputStream()) {
            String out = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        }
    }

    public static String getLicense() throws Exception {
        final String AES_SECRET_KEY = "Dat_16/05/2000's_secret_key"; // giữ nguyên như C++
        String rawInfo = buildRawInfo(); // giữ nguyên format
        System.out.println(rawInfo);
        // CryptoAPI AES → hex → SHA-256(hex)
        byte[] hexCipher = cryptoapiAesEncryptHexBytes(rawInfo, AES_SECRET_KEY);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha256.digest(hexCipher);

        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
