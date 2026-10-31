package jp.xoki.web.zunda.smarthomewebserverbackend.common.security;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.TokenHashConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 高エントロピーのトークン等の保存用ハッシュ。パスワードやファイル処理とは分離する。 */
public final class TokenHashes {

	private TokenHashes() {
	}

	/** UTF-8文字列のSHA-256を小文字16進文字列へ変換する。
	 *
	 * 空白の除去・正規化・salt追加を行わず、保存済みの64文字ハッシュとの互換性を保つ。
	 * MessageDigestは呼出しごとに生成し、可変状態をスレッド間で共有しない。
	 * @param value トークン等の元文字列
	 * @return 64文字の小文字16進ハッシュ
	 */
	public static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(TokenHashConstants.ALGORITHM)
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	/** 計算済みの固定長ハッシュと保存値を定時間比較する。
	 *
	 * 不正な保存値を正規化せず、欠損は不一致として扱う。
	 * @param computedHash 計算済みの固定長ハッシュ
	 * @param storedHash 保存済みハッシュ
	 * @return 両方が存在して同一の場合はtrue
	 */
	public static boolean matches(String computedHash, String storedHash) {
		return computedHash != null && storedHash != null && MessageDigest.isEqual(
				computedHash.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
	}
}
