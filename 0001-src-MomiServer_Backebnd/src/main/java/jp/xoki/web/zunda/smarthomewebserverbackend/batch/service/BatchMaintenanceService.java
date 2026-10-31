package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Objects;
import java.util.function.Supplier;

/** バッチの保守停止境界。
 *
 * 単一BEで開始・配信と保守状態変更を直列化する。マーカーを通常の一時ファイル清掃対象に含めない。
 * 共有DBを利用する複数BE間の排他は提供しない。
 */
@Service
public final class BatchMaintenanceService {

	private final Path marker;
	private final Path directory;
	private final ReentrantReadWriteLock gate = new ReentrantReadWriteLock(true);
	private boolean paused;

	/** 再起動後も保守状態を復元する。
	 *
	 * 存在しない場合だけ通常状態とし、破損・読取り不能を通常状態へフォールバックさせない。
	 *
	 * @param markerPath 運用設定で指定する永続マーカーのパス
	 */
	public BatchMaintenanceService(@Value("${app.batch.maintenance-marker:./data/batch-maintenance.flag}") String markerPath) {
		marker = Path.of(markerPath).toAbsolutePath().normalize();
		directory = Objects.requireNonNull(marker.getParent(), "Batch maintenance marker must be a file path");
		try {
			String stored = Files.readString(marker, StandardCharsets.UTF_8);
			if (!stored.equals(Boolean.TRUE.toString())) {
				throw new IllegalStateException("Invalid batch maintenance marker");
			}
			paused = true;
		} catch (NoSuchFileException _) {
			paused = false;
		} catch (IOException exception) {
			throw new UncheckedIOException("Cannot read batch maintenance marker", exception);
		}
	}

	/** 開始状態の判定と更新を、保守変更から保護する。
	 *
	 * 呼び出し元トランザクションがある場合はコミット・ロールバックまで排他を維持する。
	 * 結果受信や取消処理にはこのゲートを使用しない。
	 *
	 * @param action 保守状態を確認して開始・配信する処理
	 * @param <T> 処理結果の型
	 * @return 処理結果
	 */
	public <T> T withStartGate(Supplier<T> action) {
		gate.readLock().lock();
		boolean releaseAfterTransaction = TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive();
		try {
			if (releaseAfterTransaction) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCompletion(int status) {
						gate.readLock().unlock();
					}
				});
			}
			return action.get();
		} finally {
			if (!releaseAfterTransaction) {
				gate.readLock().unlock();
			}
		}
	}

	/** 現在の保守状態を取得する。 */
	public boolean isPaused() {
		gate.readLock().lock();
		try {
			return paused;
		} finally {
			gate.readLock().unlock();
		}
	}

	/** 保守状態を永続化して切り替える。
	 *
	 * 受付済みの開始・配信の完了を待つが、バッチプロセスを強制終了しない。
	 * マーカーの外部編集ではなく、この操作を使用して変更する。
	 *
	 * @param requestedPaused trueで新規開始・自動配信を停止、falseで明示再開
	 */
	public void setPaused(boolean requestedPaused) {
		gate.writeLock().lock();
		try {
			if (requestedPaused) {
				persistPause();
			} else {
				Files.deleteIfExists(marker);
			}
			paused = requestedPaused;
		} catch (IOException exception) {
			throw new UncheckedIOException("Cannot persist batch maintenance state", exception);
		} finally {
			gate.writeLock().unlock();
		}
	}

	private void persistPause() throws IOException {
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, ".batch-maintenance-", ".tmp");
		try {
			Files.writeString(temporary, Boolean.TRUE.toString(), StandardCharsets.UTF_8);
			try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
				file.force(true);
			}
			Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
