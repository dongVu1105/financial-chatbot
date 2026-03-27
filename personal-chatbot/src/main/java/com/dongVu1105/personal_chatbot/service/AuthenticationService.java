package com.dongVu1105.personal_chatbot.service;

import com.dongVu1105.personal_chatbot.dto.request.AuthenticateRequest;
import com.dongVu1105.personal_chatbot.dto.request.IntrospectRequest;
import com.dongVu1105.personal_chatbot.dto.request.LogoutRequest;
import com.dongVu1105.personal_chatbot.dto.request.RefreshTokenRequest;
import com.dongVu1105.personal_chatbot.dto.response.AuthenticateResponse;
import com.dongVu1105.personal_chatbot.dto.response.IntrospectResponse;
import com.dongVu1105.personal_chatbot.entity.InvalidatedToken;
import com.dongVu1105.personal_chatbot.entity.User;
import com.dongVu1105.personal_chatbot.exception.AppException;
import com.dongVu1105.personal_chatbot.exception.ErrorCode;
import com.dongVu1105.personal_chatbot.repository.InvalidatedTokenRepository;
import com.dongVu1105.personal_chatbot.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationService {
    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.access-token-time}")
    protected long ACCESS_TOKEN_DURATION;

    @NonFinal
    @Value("${jwt.refresh-token-time}")
    protected long REFRESH_TOKEN_DURATION;


    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;

    public AuthenticateResponse authenticate (AuthenticateRequest request) throws AppException {
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if(!authenticated){
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        return AuthenticateResponse.builder()
                .valid(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public IntrospectResponse introspect (IntrospectRequest request) throws AppException, ParseException, JOSEException {
        boolean isValid = true;
        try{
            verifyToken(request.getAccessToken(), false);
        } catch (AppException e){
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).build();
    }

    public void logout (LogoutRequest request) throws AppException, ParseException, JOSEException {
        try {
            SignedJWT signedJWT = verifyToken(request.getRefreshToken(), true); // Nếu là false => trả lỗi => ko thể logout => ko ghi blacklist => hacker dùng vân đc
            String jit = signedJWT.getJWTClaimsSet().getJWTID();
            Date expireTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            invalidatedTokenRepository.save(InvalidatedToken.builder().id(jit).expiredTime(expireTime).build());
        } catch (AppException e) {
            log.info("Token already expired");
        }

        try {
            SignedJWT refreshJWT = SignedJWT.parse(request.getRefreshToken());
            String refreshJti = refreshJWT.getJWTClaimsSet().getJWTID();
            Date refreshExpireTime = refreshJWT.getJWTClaimsSet().getExpirationTime();
            invalidatedTokenRepository.save(InvalidatedToken.builder()
                    .id(refreshJti)
                    .expiredTime(refreshExpireTime)
                    .build());
        } catch (Exception e) {
            log.info("Refresh token already expired");
        }
    }

    public AuthenticateResponse refreshToken (RefreshTokenRequest request) throws AppException, ParseException, JOSEException{
        SignedJWT signedJWT = verifyToken(request.getRefreshToken(), true);
        String jit = signedJWT.getJWTClaimsSet().getJWTID();
        Date expiredTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        invalidatedTokenRepository.save(InvalidatedToken.builder().id(jit).expiredTime(expiredTime).build());
        String username = signedJWT.getJWTClaimsSet().getSubject();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);
        return AuthenticateResponse.builder()
                .valid(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public String generateAccessToken(User user) {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("dong.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(ACCESS_TOKEN_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(jwsHeader, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    public String generateRefreshToken(User user) {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("dong.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(REFRESH_TOKEN_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", "refresh")
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(jwsHeader, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException, AppException {

        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        String tokenType = (String) signedJWT.getJWTClaimsSet().getClaim("token_type");
        if((isRefresh && !"refresh".equals(tokenType)) || (!isRefresh && !"access".equals(tokenType))){
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return signedJWT;
    }

}
