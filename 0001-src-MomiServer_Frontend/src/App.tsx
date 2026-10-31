import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { AuthProvider }  from '@/contexts/AuthContext'
import { PersonalSettingsProvider } from '@/contexts/PersonalSettingsContext'
import { ModalProvider } from '@/contexts/ModalContext'
import { PrivateRoute }  from '@/routes/PrivateRoute'
import { RoleRoute }     from '@/routes/RoleRoute'
import { Layout }        from '@/components/layout'
import { SiteTitle }     from '@/components/layout/Header'
import { DeleteModal, InfoModal } from '@/components/modals'
import { PageLoading } from '@/components/ui/page-loading'

import { WebSocketProvider as ServiceWebSocketProvider } from '@/pages/Servers/ServiceStatus/service'
import { WebSocketProvider as MachineWebSocketProvider } from '@/pages/Servers/MachineStatus/service'

const LoginPage = lazy(() => import('@/pages/Top/LoginPage').then(module => ({ default: module.LoginPage })))
const PersonalSettingsPage = lazy(() => import('@/pages/Settings/page').then(module => ({ default: module.PersonalSettingsPage })))
const TopPage = lazy(() => import('@/pages/Top/TopPage').then(module => ({ default: module.TopPage })))
const ServiceStatus = lazy(() => import('@/pages/Servers/ServiceStatus/pages').then(module => ({ default: module.ServiceStatus })))
const MachineStatus = lazy(() => import('@/pages/Servers/MachineStatus/pages').then(module => ({ default: module.MachineStatus })))
const UserMasterList = lazy(() => import('@/pages/Master/Users/List/page').then(module => ({ default: module.UserMasterList })))
const UserMasterDetail = lazy(() => import('@/pages/Master/Users/Detail/page').then(module => ({ default: module.UserMasterDetail })))
const MasterList = lazy(() => import('@/pages/Master/MasterList/page').then(module => ({ default: module.MasterList })))
const ExternalApiKeyPage = lazy(() => import('@/pages/Master/ApiKeys/page').then(module => ({ default: module.ExternalApiKeyPage })))
const ServicesMasterList = lazy(() => import('@/pages/Master/Services/List/page').then(module => ({ default: module.ServicesMasterList })))
const ServicesMasterDetail = lazy(() => import('@/pages/Master/Services/Detail/page').then(module => ({ default: module.ServicesMasterDetail })))
const ItemMasterList = lazy(() => import('@/pages/Master/Items/List/page').then(module => ({ default: module.ItemMasterList })))
const ItemMasterDetail = lazy(() => import('@/pages/Master/Items/Detail/page').then(module => ({ default: module.ItemMasterDetail })))
const MachineMasterList = lazy(() => import('@/pages/Master/Machine/List/page').then(module => ({ default: module.MachineMasterList })))
const MachineMasterDetail = lazy(() => import('@/pages/Master/Machine/Detail/page').then(module => ({ default: module.MachineMasterDetail })))
const BatchDefinitionList = lazy(() => import('@/pages/Master/Batch/Difinition/List/page').then(module => ({ default: module.BatchDefinitionList })))
const BatchDefinitionDetail = lazy(() => import('@/pages/Master/Batch/Difinition/Detail/page').then(module => ({ default: module.BatchDefinitionDetail })))
const BatchScheduleList = lazy(() => import('@/pages/Master/Batch/Schedule/List/page').then(module => ({ default: module.BatchScheduleList })))
const BatchScheduleDetail = lazy(() => import('@/pages/Master/Batch/Schedule/Detail/page').then(module => ({ default: module.BatchScheduleDetail })))
const CommonSettings = lazy(() => import('@/pages/Master/CommonSettings/page').then(module => ({ default: module.CommonSettings })))
const NotificationGlobalSettings = lazy(() => import('@/pages/Master/CommonSettings/Notifications/page').then(module => ({ default: module.NotificationGlobalSettings })))
const BatchGlobalSettings = lazy(() => import('@/pages/Master/CommonSettings/Batch/page').then(module => ({ default: module.BatchGlobalSettings })))
const SecuritySettings = lazy(() => import('@/pages/Master/CommonSettings/Security/page').then(module => ({ default: module.SecuritySettings })))
const NotificationRouteSettingList = lazy(() => import('@/pages/Master/NotificationRouteSettings/List/page').then(module => ({ default: module.NotificationRouteSettingList })))
const NotificationRouteSettingDetail = lazy(() => import('@/pages/Master/NotificationRouteSettings/Detail/page').then(module => ({ default: module.NotificationRouteSettingDetail })))
const StockList = lazy(() => import('@/pages/Stock/List/page').then(module => ({ default: module.StockList })))
const StockDetail = lazy(() => import('@/pages/Stock/Detail/page').then(module => ({ default: module.StockDetail })))
const AlertsPage = lazy(() => import('@/pages/AlertsPage').then(module => ({ default: module.AlertsPage })))
const ApiAccessLogPage = lazy(() => import('@/pages/Logs/View/page').then(module => ({ default: module.ApiAccessLogPage })))
const VehicleListPage = lazy(() => import('@/pages/Vehicles/List/page').then(module => ({ default: module.VehicleListPage })))
const VehicleDetailPage = lazy(() => import('@/pages/Vehicles/Detail/page').then(module => ({ default: module.VehicleDetailPage })))
const VehicleImportPage = lazy(() => import('@/pages/Vehicles/Import/page').then(module => ({ default: module.VehicleImportPage })))
const VehicleImportConfirmPage = lazy(() => import('@/pages/Vehicles/ImportConfirm/page').then(module => ({ default: module.VehicleImportConfirmPage })))

function ServiceWebSocketRoutes() {
	return (
	<ServiceWebSocketProvider>
		<Outlet />
	</ServiceWebSocketProvider>
	)
}

function MachineWebSocketRoutes() {
	return (
	<MachineWebSocketProvider>
		<Outlet />
	</MachineWebSocketProvider>
	)
}

export default function App() {
	return (
	<BrowserRouter>
		<ThemeProvider defaultTheme="dark">
		<AuthProvider>
		<PersonalSettingsProvider>
			<ModalProvider>
			<DeleteModal />
			<InfoModal />

			<Suspense fallback={<PageLoading />}>
			<Routes>

				{/*画面名: ログイン画面*/}
				<Route path="/login" element={<LoginPage />} />

				{/*ログイン必須画面*/}
				<Route element={<PrivateRoute />}>
					<Route element={<Layout title={<SiteTitle />} />}>

						{/*WebSocket実行関係*/}
						<Route element={<ServiceWebSocketRoutes />}>
							{/*画面名: サービスステータス*/}
							<Route path="/service/status"  element={<ServiceStatus />} />
							<Route path="/service/status/:serverId"  element={<ServiceStatus />} />
						</Route>
						<Route element={<RoleRoute allowedRoles={['ADMIN', 'OPERATOR']} />}>
							<Route element={<MachineWebSocketRoutes />}>
								{/*画面名: マシンステータス*/}
								<Route path="/machine/status" element={<MachineStatus />} />
								<Route path="/machine/status/:serverId" element={<MachineStatus />} />
							</Route>
						</Route>

						{/*表示可能権限: ADMIN */}
						<Route element={<RoleRoute allowedRoles={['ADMIN']} />}>
						{/*画面名: マスタ一覧*/}
						<Route path="/master" element={<MasterList />} />
						<Route path="/master/api" element={<ExternalApiKeyPage />} />

						{/*画面名: マスタ共通設定*/}
						<Route path="/master/common-settings" element={<CommonSettings />} />

						{/*画面名: マスタ共通設定: 通知関連*/}
						<Route path="/master/common-settings/notifications" element={<NotificationGlobalSettings />} />
						
						{/*画面名: マスタ共通設定: バッチ関連*/}
						<Route path="/master/common-settings/batch" element={<BatchGlobalSettings />} />
						<Route path="/master/common-settings/security" element={<SecuritySettings />} />

						{/*画面名: ユーザマスタ*/}
						<Route path="/master/users" element={<UserMasterList />} />
						<Route path="/master/users/:userId" element={<UserMasterDetail />} />

						{/*画面名: サービスマスタ*/}
						<Route path="/master/services" element={<ServicesMasterList />} />
						<Route path="/master/services/:serverId" element={<ServicesMasterDetail />} />

						{/*画面名: マシンマスタ*/}
						<Route path="/master/machine" element={<MachineMasterList />} />
						<Route path="/master/machine/:machineID" element={<MachineMasterDetail />} />

						{/*画面名: 在庫品目マスタ*/}
						<Route path="/master/items" element={<ItemMasterList />} />
						<Route path="/master/items/:itemId" element={<ItemMasterDetail />} />

						{/*画面名: バッチ定義マスタ*/}
						<Route path="/master/batch/difinition" element={<BatchDefinitionList />} />
						<Route path="/master/batch/difinition/:batchDefinitionId" element={<BatchDefinitionDetail />} />

						{/*画面名: バッチスケジュールマスタ*/}
						<Route path="/master/batch/schedule" element={<BatchScheduleList />} />
						<Route path="/master/batch/schedule/:batchScheduleId" element={<BatchScheduleDetail />} />
						{/*画面名: 通知ルートマスタ*/}
						<Route path="/master/notification-route-settings" element={<NotificationRouteSettingList />} />
						<Route path="/master/notification-route-settings/:routeSettingId" element={<NotificationRouteSettingDetail />} />
						</Route>

						{/*表示可能権限: ADMIN / OPERATOR*/}
						<Route element={<RoleRoute allowedRoles={['ADMIN', 'OPERATOR']} />}>
						{/*画面名: APIログ*/}
						<Route path="/logs/api" element={<ApiAccessLogPage />} />
						{/*画面名: 車両新規登録*/}
						<Route path="/vehicles/new" element={<VehicleDetailPage />} />
						{/*画面名: 車検証PDF・画像取込*/}
						<Route path="/vehicles/import" element={<VehicleImportPage />} />
						<Route path="/vehicles/import/:importId/confirm" element={<VehicleImportConfirmPage />} />
						</Route>

						{/*表示可能権限: 全権限表示可能*/}
						<Route index element={<Navigate to="/index" replace />} />
						
						{/*画面名: 在庫管理*/}
						<Route path="/stock" element={<StockList />} />
						<Route path="/stock/:itemId" element={<StockDetail />} />

						{/*画面名: 車両管理*/}
						<Route path="/vehicles" element={<VehicleListPage />} />
						<Route path="/vehicles/:vehicleId" element={<VehicleDetailPage />} />

						{/*画面名: 初期ページ*/}
						<Route path="/index"  element={<TopPage />} />
						<Route path="/settings" element={<PersonalSettingsPage />} />

						{/*画面名: アラート画面*/}
						<Route path="/alerts" element={<AlertsPage />} />
					</Route>{/*<Route element={<Layout title={<SiteTitle />} />}> 終了タグ*/}
				</Route>{/*<Route element={<PrivateRoute />}> 終了タグ*/}
				
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
			</Suspense>
			</ModalProvider>
		</PersonalSettingsProvider>
		</AuthProvider>
		</ThemeProvider>
	</BrowserRouter>
	)
}
