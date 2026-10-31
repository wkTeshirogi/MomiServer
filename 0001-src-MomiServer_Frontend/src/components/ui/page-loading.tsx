import mapleLoadingUrl from '@/assets/oo-maple-loading.svg'

export function PageLoading() {
	return (
		<div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-zinc-50 p-6 dark:bg-zinc-950">
			<img
				src={mapleLoadingUrl}
				alt=""
				aria-hidden="true"
				className="h-40 w-40 select-none object-contain sm:h-48 sm:w-48"
			/>
			<p role="status" aria-live="polite" className="text-sm text-zinc-500 dark:text-zinc-400">
				読み込み中...
			</p>
		</div>
	)
}
