package com.example.auth_bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OIDC Discoveryエンドポイントから取得するメタデータを格納するDTO
 *
 * <p>end_session_endpointなど、必要なプロパティのみを定義する。</p>
 */
@Data
public class OidcConfiguration {

    /**
     * OpenID Connect RP-Initiated LogoutのためのエンドポイントURL
     * 例: "http://localhost:8080/realms/my-realm/protocol/openid-connect/logout"
     */
    @JsonProperty("end_session_endpoint")
    private String endSessionEndpoint;

}
