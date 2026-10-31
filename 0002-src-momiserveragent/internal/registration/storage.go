package registration

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
)

// prepareDirectory は保存先を作成し、実ディレクトリであることとOS別の権限制限を確認する。
// 作成・検証・権限設定の失敗はエラーにする。既存ディレクトリの権限も変更する。
func prepareDirectory(dir string) error {
	if err := os.MkdirAll(dir, 0700); err != nil {
		return errors.New("保存先を作成できません。実行権限を確認してください")
	}
	info, err := os.Lstat(dir)
	if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return errors.New("保存先には実ディレクトリを指定してください")
	}
	return securePath(dir, true)
}

// loadState は資格情報ファイルを権限制限して復元する。
// 未作成はnil・nilを返す。非通常ファイル・破損・読取失敗はエラーとし、自動上書きしない。
func loadState(dir string) (*State, error) {
	path := filepath.Join(dir, "credentials.json")
	info, err := os.Lstat(path)
	if os.IsNotExist(err) {
		return nil, nil
	}
	if err != nil || !info.Mode().IsRegular() {
		return nil, errors.New("認証情報ファイルが不正です")
	}
	if err = securePath(path, false); err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, errors.New("認証情報を読み取れません")
	}
	var state State
	if err = json.Unmarshal(data, &state); err != nil || state.Server == "" || state.PrivateKey == "" || state.RegistrationRequestID == "" {
		return nil, errors.New("認証情報が破損しています。上書きせず管理者へ確認してください")
	}
	return &state, nil
}

// saveState は非nilの資格情報を一時ファイルへ保存し、同期後に既存ファイルを置換する。
// 成功・失敗にかかわらず一時ファイルの解放・削除を試みる。保存先の排他ロックは呼び出し元が保持する。
// 後始末のエラーは主処理を上書きせず、Write・Sync・明示Closeの失敗で保存可否を判断する。
// ディレクトリ同期は置換後なので、エラーだけで旧ファイルのままとは断定できない。
func saveState(dir string, state *State) error {
	data, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		return errors.New("認証情報を構成できません")
	}
	file, err := os.CreateTemp(dir, ".credentials-*")
	if err != nil {
		return errors.New("認証情報を保存できません")
	}
	temp := file.Name()
	defer os.Remove(temp)
	defer file.Close()

	// 特にWindowsでは親ディレクトリから権限を継承するため、秘密値を書き込む前に一時ファイルを保護する。
	if err = securePath(temp, false); err != nil {
		return err
	}
	if _, err = file.Write(data); err != nil {
		return errors.New("認証情報を書き込めません。同じ操作を再試行してください")
	}
	if err = file.Sync(); err != nil {
		return errors.New("認証情報の永続化に失敗しました")
	}
	if err = file.Close(); err != nil {
		return errors.New("認証情報を確定できません")
	}

	// 書込み・同期・Closeがすべて成功したファイルだけを置換し、途中まで書けた資格情報で旧ファイルを壊さない。
	if err = replaceFile(temp, filepath.Join(dir, "credentials.json")); err != nil {
		return errors.New("認証情報を置換できません。同じ操作を再試行してください")
	}
	return syncDirectory(dir)
}
