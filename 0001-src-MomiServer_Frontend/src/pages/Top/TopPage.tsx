import { NotificationBanner } from '@/components/ui/notification-banner'
import { Button }         from '@/components/ui/button'
import { SendButton }     from '@/components/ui/send-button'
import { Card }           from '@/components/ui/card'
import { useModalManager } from '@/contexts/ModalContext'
import { INFO_MODAL_ID } from '@/components/modals'
import {
	getNotificationLevelClass,
	getNotificationLevelLabel,
	getNotificationTypeLabel,
	useNotificationList,
	type NotificationItem,
} from '@/pages/Notifications/service'

// ---------------------------定数定義-------------------------------

const servers = [
	{
		id: 1,
		name: '7Days To Die',
		status: '● online',
		cpu: '100%',
		mem: '100%',
		worktime: 'dd day hh:mm',
		version: 'Alpha Zundamochi',
	},
	{
		id: 2,
		name: 'Minecraft JE',
		status: '● online',
		cpu: '100%',
		mem: '100%',
		worktime: 'dd day hh:mm',
		version: '26.2.1',
	},
	{
		id: 3,
		name: 'CoreKeeper',
		status: '● online',
		cpu: '100%',
		mem: '100%',
		worktime: 'dd day hh:mm',
		version: '10.10.10',
	},
	{
		id: 4,
		name: 'もっちもち',
		status: '● online',
		cpu: '100%',
		mem: '100%',
		worktime: 'dd day hh:mm',
		version: 'MochiMochi',
	},
]

export function TopPage() {
	const { openModal } = useModalManager()
	const {
		items: noticeList,
		loading: noticesLoading,
		error: noticesError,
		updating: noticesUpdating,
		markRead,
	} = useNotificationList({ limit: 5 })
	// ---------------------------各種アクション定義-------------------------------

	const handleOpenNotice = (notice: NotificationItem) => {
		openModal(INFO_MODAL_ID, {
			title: `${notice.title} (${getNotificationLevelLabel(notice.level)})`,
			message: notice.message,
			size: 'xl'
		})
		void markRead(notice.notification_id)
	}

	// サーバ状況取得
	
	

	// ---------------------------以下画面定義-------------------------------
	return (
		<div className="space-y-6 text-left">
			<section className="space-y-2">
				<div className="flex items-center justify-between gap-3">
					<h1 className="!my-0 flex items-center !text-xl !font-semibold !tracking-normal text-zinc-900 dark:text-white">
						通知一覧
					</h1>
					<Button href="/alerts" variant="outline" size="sm">
						アラート一覧
					</Button>
				</div>
				{!noticesLoading && noticesError && <NotificationBanner type="error">{noticesError}</NotificationBanner>}
				<div className="overflow-x-auto border border-gray-700 rounded-lg">
					<div className="overflow-y-auto">
						<table className="w-full min-w-[680px] table-auto text-sm">
							<thead className="sticky top-0 z-10 bg-white dark:bg-zinc-900">
								<tr className="border-b border-gray-700">
									<th className="w-40 px-2 py-2 text-left font-semibold whitespace-nowrap">時刻</th>
									<th className="w-24 px-2 py-2 text-left font-semibold whitespace-nowrap">緊急度</th>
									<th className="w-28 px-2 py-2 text-left font-semibold whitespace-nowrap">種別</th>
									<th className="px-2 py-2 text-left font-semibold">内容</th>
									<th className="w-20 px-2 py-2 text-right font-semibold">操作</th>
								</tr>
							</thead>
							<tbody>
								{noticesLoading && (
									<tr>
										<td className="px-2 py-4 text-zinc-500" colSpan={5}>通知を読み込み中です。</td>
									</tr>
								)}
								{!noticesLoading && !noticesError && noticeList.length === 0 && (
									<tr>
										<td className="px-2 py-4 text-zinc-500" colSpan={5}>表示できる通知はありません。</td>
									</tr>
								)}
								{!noticesLoading && noticeList.map((data) => (
									<tr key={data.id} className="border-b border-gray-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors">
										<td className="w-40 px-2 py-2 whitespace-nowrap">{data.createdAt}</td>
										<td className="w-24 px-2 py-2 whitespace-nowrap">
											<span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${getNotificationLevelClass(data.level)}`}>
												{getNotificationLevelLabel(data.level)}
											</span>
										</td>
										<td className="w-28 px-2 py-2 whitespace-nowrap">
											{getNotificationTypeLabel(data.notification_type)}
										</td>
										<td className="px-2 py-2 break-words">
											<div className="font-semibold text-zinc-900 dark:text-white">{data.title}</div>
											<div className="mt-1 line-clamp-2 whitespace-pre-wrap text-zinc-600 dark:text-zinc-300">
												{data.message}
											</div>
										</td>
										<td className="px-2 py-2 text-right">
											<SendButton disabled={noticesUpdating} onClick={() => handleOpenNotice(data)}>
												詳細
											</SendButton>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</div>
			</section>

			<section className="space-y-2">
				<h1 className="!my-0 flex items-center !text-xl !font-semibold !tracking-normal text-zinc-900 dark:text-white">
					各種サーバー状況
				</h1>
				<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
					{servers.map((server) => (
						<Card key={server.name} className="w-full min-w-0 rounded-sm border dark:border-gray-700 p-3">
							<h3 className="flex border-b border-gray-700 pb-1 text-lg font-semibold break-words dark:text-white text-black">
								{server.name}
							</h3>
							<div className="grid grid-cols-[minmax(4.5rem,auto)_minmax(0,1fr)] gap-x-3 gap-y-1 pt-2 text-sm">
								<div className="font-bold border-b border-gray-900 dark:text-white text-black">Status</div>
								<div className="min-w-0 border-b border-gray-900 break-words dark:text-white text-black">{server.status}</div>

								<div className="font-bold border-b border-gray-900 dark:text-white text-black">CPU</div>
								<div className="min-w-0 border-b border-gray-900 break-words dark:text-white text-black">{server.cpu}</div>

								<div className="font-bold border-b border-gray-900 dark:text-white text-black">Mem</div>
								<div className="min-w-0 border-b border-gray-900 break-words dark:text-white text-black">{server.mem}</div>

								<div className="font-bold border-b border-gray-900 dark:text-white text-black">稼働時間</div>
								<div className="min-w-0 border-b border-gray-900 break-words dark:text-white text-black">{server.worktime}</div>

								<div className="font-bold border-b border-gray-900 dark:text-white text-black">Version</div>
								<div className="min-w-0 font-bold border-b border-gray-900 break-words dark:text-white text-black">{server.version}</div>
								<div></div>
								<div className="text-right">
									<Button href={"/service/status/" + server.id} variant="default" label="詳細"/>
								</div>
							</div>
						</Card>
					))}
				</div>
			</section>
			<section className="space-y-2">
				<h1 className="!my-0 flex items-center !text-xl !font-semibold !tracking-normal text-zinc-900 dark:text-white">
					○○○状況
				</h1>
				<div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
				</div>
			</section>
		</div>
	)
}
