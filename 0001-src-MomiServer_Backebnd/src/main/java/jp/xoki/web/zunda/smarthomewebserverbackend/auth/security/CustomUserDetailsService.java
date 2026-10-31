package jp.xoki.web.zunda.smarthomewebserverbackend.auth.security;

import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** DB上のユーザー情報をSpring SecurityのUserDetailsへ変換するサービス。 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	/**
	 * メールアドレスをキーにユーザーと権限を読み込む。
	 * @param username 認証対象のメールアドレス
	 * @return Spring Securityが使用するユーザー情報
	 * @throws UsernameNotFoundException 対応するユーザーが存在しない場合
	 */
	@Override

	/**
	 * メールアドレスをキーにユーザー情報を読み込む。
	 *
	 * @param username 認証対象のメールアドレス
	 * @return Spring Securityが使用するユーザー情報
	 * @throws UsernameNotFoundException 対象ユーザーが存在しない場合
	 */
	public UserDetails loadUserByUsername(String username)
			throws UsernameNotFoundException {

		// UsersRecord: jOOQが生成したレコードクラス（テーブル定義クラスの Users とは別物）
		MUsersRecord databaseRecord = userRepository.findByEmail(username)
				.orElseThrow(() ->
						new UsernameNotFoundException(
								"User not found: " + username));

		return new org.springframework.security.core.userdetails.User(
				databaseRecord.getEmail(),      // username の代わりに email を使用
				databaseRecord.getPassword(),
				List.of(new SimpleGrantedAuthority("ROLE_" + databaseRecord.getRole()))
		);
	}
}
