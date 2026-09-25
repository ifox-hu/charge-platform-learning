package com.chargeplatform.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER=Base64.getUrlDecoder();
    private final ObjectMapper objectMapper; private final byte[] secret; private final long expirationSeconds;
    public JwtService(ObjectMapper objectMapper,@Value("${app.jwt.secret}") String secret,@Value("${app.jwt.expiration-minutes}") long expirationMinutes){
        this.objectMapper=objectMapper;this.secret=secret.getBytes(StandardCharsets.UTF_8);this.expirationSeconds=expirationMinutes*60;
    }
    public String create(String username,String role){
        try{
            String header=encode(objectMapper.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
            long now=Instant.now().getEpochSecond(); Map<String,Object> payload=new LinkedHashMap<>();payload.put("sub",username);payload.put("role",role);payload.put("iat",now);payload.put("exp",now+expirationSeconds);
            String body=encode(objectMapper.writeValueAsBytes(payload)); String content=header+"."+body;
            return content+"."+encode(sign(content));
        }catch(Exception exception){throw new IllegalStateException("JWT 创建失败",exception);}
    }
    public Map<String,Object> parse(String token){
        try{
            String[] parts=token.split("\\."); if(parts.length!=3) throw new IllegalArgumentException("令牌格式错误");
            byte[] expected=sign(parts[0]+"."+parts[1]); byte[] actual=URL_DECODER.decode(parts[2]);
            if(!java.security.MessageDigest.isEqual(expected,actual)) throw new IllegalArgumentException("令牌签名无效");
            Map<String,Object> claims=objectMapper.readValue(URL_DECODER.decode(parts[1]),new TypeReference<>(){});
            if(((Number)claims.get("exp")).longValue()<Instant.now().getEpochSecond()) throw new IllegalArgumentException("令牌已过期");
            return claims;
        }catch(IllegalArgumentException exception){throw exception;}catch(Exception exception){throw new IllegalArgumentException("令牌解析失败",exception);}
    }
    public long getExpirationSeconds(){return expirationSeconds;}
    private byte[] sign(String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}
    private String encode(byte[] value){return URL_ENCODER.encodeToString(value);}
}
