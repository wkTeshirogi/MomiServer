//go:build windows

package registration

import (
	"errors"
	"golang.org/x/sys/windows"
	"os"
	"path/filepath"
)

// securePath は現在の実行アカウントとSYSTEMだけにアクセスを許可する保護DACLを設定する。
// directoryがtrueの場合は子へ継承する。既存ACLを置換し、設定不能時はエラーとする。
func securePath(path string, directory bool) error {
	token := windows.GetCurrentProcessToken()
	user, err := token.GetTokenUser()
	if err != nil {
		return errors.New("実行アカウントを確認できません")
	}
	flags := ""
	if directory {
		flags = "OICI"
	}
	descriptor, err := windows.SecurityDescriptorFromString("D:P(A;" + flags + ";FA;;;SY)(A;" + flags + ";FA;;;" + user.User.Sid.String() + ")")
	if err != nil {
		return errors.New("アクセス権を作成できません")
	}
	acl, _, err := descriptor.DACL()
	if err != nil {
		return errors.New("アクセス権を読み取れません")
	}
	err = windows.SetNamedSecurityInfo(path, windows.SE_FILE_OBJECT,
		windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION, nil, nil, acl, nil)
	if err != nil {
		return errors.New("認証情報のアクセス権を制限できません")
	}
	return nil
}

// lockDirectory は保存先のロックファイルを非待機・排他モードで保持する。
// 競合・作成失敗はエラーにする。成功時は呼び出し元が返却関数を一度呼び、ロックとファイルを解放する。
// 解放関数は主処理の結果を上書きせず、アンロック・Closeを最善努力で実施する。
func lockDirectory(dir string) (func(), error) {
	file, err := os.OpenFile(filepath.Join(dir, ".registration.lock"), os.O_CREATE|os.O_RDWR, 0600)
	if err != nil {
		return nil, errors.New("登録処理のロックを作成できません")
	}
	overlapped := new(windows.Overlapped)
	err = windows.LockFileEx(windows.Handle(file.Fd()), windows.LOCKFILE_EXCLUSIVE_LOCK|windows.LOCKFILE_FAIL_IMMEDIATELY,
		0, 1, 0, overlapped)
	if err != nil {
		file.Close()
		return nil, errors.New("別のAgent登録・認証処理が実行中です")
	}
	return func() { windows.UnlockFileEx(windows.Handle(file.Fd()), 0, 1, 0, overlapped); file.Close() }, nil
}

// replaceFile は一時ファイルを既存保存先へWRITE_THROUGH付きで置換する。
// UTF-16変換や置換の失敗は呼び出し元へ返す。
func replaceFile(source, target string) error {
	from, err := windows.UTF16PtrFromString(source)
	if err != nil {
		return err
	}
	to, err := windows.UTF16PtrFromString(target)
	if err != nil {
		return err
	}
	return windows.MoveFileEx(from, to, windows.MOVEFILE_REPLACE_EXISTING|windows.MOVEFILE_WRITE_THROUGH)
}

// syncDirectory はWindowsで追加の同期を行わず成功を返す。
// 永続化は直前のreplaceFileが指定するWRITE_THROUGHで扱う。
func syncDirectory(_ string) error { return nil }
