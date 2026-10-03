import BackgroundTasks
import UIKit

enum BackgroundScheduler {
    static let identifier = "pl.sergeyst.gdanskmonitor.ios.refresh"
    static func cancel() { BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier) }
    @discardableResult static func schedule(_ settings: AppSettings) -> Bool {
        cancel()
        guard settings.autoRefresh else { return true }
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date().addingTimeInterval(TimeInterval(settings.interval * 60))
        do { try BGTaskScheduler.shared.submit(request); return true }
        catch { return false }
    }
}

private final class BackgroundCompletion {
    let task: BGTask
    private let lock = NSLock()
    private var finished = false
    init(_ task: BGTask) { self.task = task }
    func finish(_ success: Bool) {
        lock.lock(); let deliver = !finished; finished = true; lock.unlock()
        if deliver { task.expirationHandler = nil; task.setTaskCompleted(success: success) }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: BackgroundScheduler.identifier, using: .main) { task in
            let completion = BackgroundCompletion(task)
            let work = Task { @MainActor in
                let store = MonitorStore.shared
                store.reloadStorage()
                guard store.settings.autoRefresh else { completion.finish(true); return }
                BackgroundScheduler.schedule(store.settings)
                let success = await store.check(background: true)
                completion.finish(success)
            }
            task.expirationHandler = { work.cancel(); completion.finish(false) }
        }
        return true
    }
}
