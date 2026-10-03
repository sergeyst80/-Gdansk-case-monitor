import SwiftUI

@main
@MainActor
struct GdanskCaseMonitorApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var store = MonitorStore.shared
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            DashboardView().environmentObject(store)
                .environment(\.locale, Locale(identifier: store.settings.language))
                .environment(\.layoutDirection, store.settings.language == "ar" ? .rightToLeft : .leftToRight)
                .tint(Color(red: 0.03, green: 0.50, blue: 0.55))
                .onChange(of: scenePhase) { _, phase in
                    if phase == .background { BackgroundScheduler.schedule(store.settings) }
                    if phase == .active { store.reloadStorage() }
                }
        }
    }
}
