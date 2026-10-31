package registration

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// attachment は専用作業領域に取得する添付の相対配置先と整合性情報を表す。
type attachment struct {
	// ID は実行要求の添付ダウンロードAPIで使用するファイル識別子を表す。
	ID int64 `json:"id"`
	// Destination は作業領域内の相対パスを表し、配置前に移動・予約名・重複を検証する。
	Destination string `json:"destination_path"`
	// Size はダウンロードした実データと照合するバイト数を表す。
	Size int64 `json:"file_size"`
	// Hash は小文字16進表現のSHA-256で、取得後の整合性確認に使用する。
	Hash string `json:"checksum_sha256"`
}

// validDestination はOS間で扱える添付の相対配置先を判定する。
// 空値、階層移動、絶対パス、制御文字、Windows予約名、長さ超過を拒否する。
func validDestination(value string) bool {
	if value == "" || len([]rune(value)) > 500 || strings.ContainsAny(value, "\\:*?\"<>|") {
		return false
	}
	for _, part := range strings.Split(value, "/") {
		if part == "" || part == "." || part == ".." || strings.HasSuffix(part, ".") || strings.HasSuffix(part, " ") || len([]rune(part)) > 100 {
			return false
		}
		for _, c := range part {
			if c < 32 || c == 127 {
				return false
			}
		}
		base := strings.ToUpper(strings.Split(part, ".")[0])
		if base == "CON" || base == "PRN" || base == "AUX" || base == "NUL" || (len(base) == 4 && (strings.HasPrefix(base, "COM") || strings.HasPrefix(base, "LPT")) && base[3] >= '0' && base[3] <= '9') {
			return false
		}
	}
	return true
}

// workRoot は保護された保存先配下のworkだけを開く。
// 成功時のos.Rootは呼び出し元がCloseする。個別実行の子階層には検証済みIDだけを使用する。
func workRoot(dir string) (*os.Root, error) {
	path := filepath.Join(dir, "work")
	if err := prepareDirectory(path); err != nil {
		return nil, err
	}
	return os.OpenRoot(path)
}

// prepareAttachments は新規作業領域へ添付を取得し、サイズ・ハッシュを照合する。
// 既存領域は再利用せず、不正・重複する配置先を拒否する。ctxが取得期限とキャンセルを制御する。
// 成功時は作業パスを返し、失敗時はコマンドを開始しない。途中の作業領域は残る場合がある。
// 取得Body・ファイル・os.Rootは内部で閉じ、呼び出し元には所有権を渡さない。
func prepareAttachments(ctx context.Context, dir string, client *Client, token string, e execution) (string, error) {
	if !validExecutionID(e.ID) {
		return "", errors.New("invalid execution ID")
	}
	work, err := workRoot(dir)
	if err != nil {
		return "", err
	}
	defer work.Close()

	// 前回の実行ファイルが残っている可能性があるため、既存の作業領域を再利用しない。
	if err = work.Mkdir(e.ID, 0700); err != nil {
		return "", err
	}
	root, err := work.OpenRoot(e.ID)
	if err != nil {
		return "", err
	}
	defer root.Close()
	paths := []string{}
	if len(e.Attachments) > 1000 {
		return "", errors.New("invalid manifest")
	}
	for _, f := range e.Attachments {

		// 大文字小文字を区別しないOSでも衝突しないよう正規化し、ファイルと親ディレクトリの兼用も拒否する。
		key := strings.ToLower(f.Destination)
		if !validDestination(f.Destination) || f.ID <= 0 || f.Size < 0 || f.Size > 10240*1048576 || len(f.Hash) != 64 {
			return "", errors.New("invalid attachment")
		}
		for _, other := range paths {
			if key == other || strings.HasPrefix(key, other+"/") || strings.HasPrefix(other, key+"/") {
				return "", errors.New("duplicate attachment path")
			}
		}
		paths = append(paths, key)
		if err = root.MkdirAll(filepath.Dir(filepath.FromSlash(f.Destination)), 0700); err != nil {
			return "", err
		}
		req, err := http.NewRequestWithContext(ctx, "GET", fmt.Sprintf("%s/api/v1/agent/batch/executions/%s/attachments/%d", client.Server, e.ID, f.ID), nil)
		if err != nil {
			return "", err
		}
		req.Header.Set("Authorization", "Bearer "+token)

		// リダイレクト・ループバックの保護を引き継ぎ、大きな転送の期限は実行contextで制限する。
		downloader := *client.HTTP
		downloader.Timeout = 0
		response, err := downloader.Do(req)
		if err != nil {
			return "", errors.New("添付取得に失敗しました")
		}
		if response.StatusCode != http.StatusOK {
			response.Body.Close()
			return "", errors.New("添付取得が拒否されました")
		}
		out, err := root.OpenFile(filepath.FromSlash(f.Destination), os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
		if err != nil {
			response.Body.Close()
			return "", err
		}
		hash := sha256.New()

		// 宣言サイズを1バイト超えて読むことで、過大な応答を無制限に保存せず、サイズ超過も検出する。
		count, copyErr := io.Copy(io.MultiWriter(out, hash), io.LimitReader(response.Body, f.Size+1))
		response.Body.Close()
		syncErr := out.Sync()
		closeErr := out.Close()
		if copyErr != nil || syncErr != nil || closeErr != nil || count != f.Size || hex.EncodeToString(hash.Sum(nil)) != f.Hash {
			return "", errors.New("添付のサイズ・ハッシュ検証に失敗しました")
		}
	}
	return filepath.Join(dir, "work", e.ID), nil
}

// executeWithAttachments は添付準備も実行期限に含めてコマンドを実行する。
// cancelは呼び出し元が閉じ、準備を監視するgoroutineはキャンセルまたは準備context終了で抜ける。
// 準備失敗・期限超過・キャンセルは未開始として報告し、Agent終了時は結果不明を返す。
// 旧要求のWorkRetentionDaysが0の場合は従来の作業ディレクトリ契約を維持する。
func executeWithAttachments(ctx context.Context, dir string, client *Client, token string, e execution, config runtimeConfig, cancel <-chan struct{}) result {
	// 新要求は保持期間を持つため、0の場合にだけ旧ディレクトリ契約を維持する。
	if e.WorkRetentionDays == 0 {
		return executeProcess(ctx, e, config, cancel)
	}
	if e.WorkRetentionDays < 1 || e.WorkRetentionDays > 3650 {
		return result{Status: executionFailed, NotStarted: true, StopConfirmed: true, Message: "作業領域設定が不正です"}
	}
	preparation, stop := context.WithTimeout(ctx, time.Duration(e.Timeout)*time.Second)
	defer stop()
	// 監視用goroutineはキャンセルまたは準備context終了で抜け、channelは呼び出し元だけが閉じる。
	go func() {
		select {
		case <-cancel:
			stop()
		case <-preparation.Done():
		}
	}()
	started := time.Now()
	path, err := prepareAttachments(preparation, dir, client, token, e)
	if ctx.Err() != nil {
		return result{Status: executionUnknown, Message: "Agent停止中に準備が中断されました"}
	}
	select {
	case <-cancel:
		return result{Status: executionCancelled, NotStarted: true, StopConfirmed: true}
	default:
	}
	if preparation.Err() != nil {
		return result{Status: executionTimeout, NotStarted: true, StopConfirmed: true, Message: "ファイル準備がタイムアウトしました"}
	}
	if err != nil {
		return result{Status: executionFailed, NotStarted: true, StopConfirmed: true, Message: "ファイル準備失敗。スクリプトは開始していません"}
	}
	e.Directory = path

	// 添付準備とコマンド実行で別々に全制限時間を使わず、準備で消費した秒数を実行予算から差し引く。
	e.Timeout -= int(time.Since(started).Seconds())
	if e.Timeout < 1 {
		return result{Status: executionTimeout, NotStarted: true, StopConfirmed: true}
	}
	return executeProcess(ctx, e, config, cancel)
}

// cleanupWork は停止確認と保持期限を満たす実行済み作業領域だけを回収する。
// 結果不明・実行中は対象外とし、検証済みIDをos.Root配下で削除して完了を保存する。
// 削除・保存の失敗はエラーとし、常駐ループが次周期で再試行する。
func (d *daemon) cleanupWork(now time.Time) error {
	for _, j := range d.journals {
		if j.WorkCleaned || j.CleanupAfter.IsZero() || now.Before(j.CleanupAfter) || !j.WorkStopped || d.active[j.Execution.ID] != nil {
			continue
		}
		if !validExecutionID(j.Execution.ID) {
			return errors.New("invalid cleanup ID")
		}
		root, err := workRoot(d.dir)
		if err != nil {
			return err
		}
		err = root.RemoveAll(j.Execution.ID)
		root.Close()
		if err != nil {
			return err
		}
		j.WorkCleaned = true
		if err = d.save(j); err != nil {
			return err
		}
	}
	return nil
}
