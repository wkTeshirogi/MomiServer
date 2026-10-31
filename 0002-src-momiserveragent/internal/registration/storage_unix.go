//go:build linux || darwin

package registration

import (
	"errors"
	"golang.org/x/sys/unix"
	"os"
	"path/filepath"
)

// securePath はディレクトリを0700、ファイルを0600へ制限する。
// 既存のモードを置換し、変更できない場合はエラーを返す。
func securePath(path string, directory bool) error {
	mode := os.FileMode(0600)
	if directory {
		mode = 0700
	}
	if err := os.Chmod(path, mode); err != nil {
		return errors.New("認証情報のアクセス権を制限できません")
	}
	return nil
}

// lockDirectory は保存先を非待機の排他flockで保持する。
// 競合・作成失敗はエラーにする。成功時は呼び出し元が返却関数を一度呼び、ロックとファイルを解放する。
// 解放関数は主処理の結果を上書きせず、アンロック・Closeを最善努力で実施する。
func lockDirectory(dir string) (func(), error) {
	file, err := os.OpenFile(filepath.Join(dir, ".registration.lock"), os.O_CREATE|os.O_RDWR, 0600)
	if err != nil {
		return nil, errors.New("登録処理のロックを作成できません")
	}
	if err = unix.Flock(int(file.Fd()), unix.LOCK_EX|unix.LOCK_NB); err != nil {
		file.Close()
		return nil, errors.New("別のAgent登録・認証処理が実行中です")
	}
	return func() { unix.Flock(int(file.Fd()), unix.LOCK_UN); file.Close() }, nil
}

// replaceFile は同じ保存先内の一時ファイルをrenameで置換し、失敗をそのまま返す。
func replaceFile(source, target string) error { return os.Rename(source, target) }

// syncDirectory は置換後のディレクトリエントリーを同期する。
// 内部で開いたファイルは閉じ、Open・Syncに失敗した場合はエラーとする。
func syncDirectory(dir string) error {
	file, err := os.Open(dir)
	if err != nil {
		return errors.New("保存ディレクトリを開けません")
	}
	defer file.Close()
	if err = file.Sync(); err != nil {
		return errors.New("保存ディレクトリを永続化できません")
	}
	return nil
}
