// momiserver-agent は登録・認証状態の確認とバッチ常駐処理を提供するCLIである。
// 失敗は秘密値を含まないメッセージと終了コード1で通知する。
package main

import (
	"fmt"
	"os"

	"momiserver-agent/internal/registration"
)

func main() {
	if err := registration.Run(os.Args[1:], os.Stdin, os.Stdout); err != nil {
		// 呼び出し先のエラーは要求本文・トークン・コード・秘密鍵を含まない文言に限定する。
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
