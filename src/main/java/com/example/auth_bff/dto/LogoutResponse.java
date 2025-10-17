package com.example.auth_bff.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ログアウトレスポンス
 *
 * <p>ログアウト処理の結果を返却するDTO。</p>
 *
 * <h3>フィールド:</h3>
 * <ul>
 *   <li><b>message</b>: ログアウト成功メッセージ（通常は "success"）</li>
 *   <li><b>warning</b>: Keycloakログアウト失敗時の警告メッセージ（成功時はnull）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {
    /**
     * ログアウトメッセージ
     * 通常は "success"
     */
    private String message;

    /**
     * 警告メッセージ（オプション）
     * Keycloakログアウトに失敗した場合のみ設定される
     * 例: "Keycloakログアウトに失敗しました。再度お試しください。"
     */
    private String warning;

    /**
     * 警告なしのログアウトレスポンスを作成
     * @param message ログアウトメッセージ
     */
    public LogoutResponse(String message) {
        this.message = message;
        this.warning = null;
    }
}