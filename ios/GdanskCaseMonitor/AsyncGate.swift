import Foundation

@MainActor
final class AsyncGate<Value> {
    private var continuation: CheckedContinuation<Value, Error>?
    private var resolved: Result<Value, Error>?
    private var timeoutTask: Task<Void, Never>?
    func attach(_ continuation: CheckedContinuation<Value, Error>, timeout: UInt64) {
        if let resolved { continuation.resume(with: resolved); return }
        self.continuation = continuation
        timeoutTask = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: timeout * 1_000_000_000) }
            catch { return }
            self?.resolve(.failure(MonitorError.message("timeout_error")))
        }
    }
    func resolve(_ result: Result<Value, Error>) {
        guard resolved == nil else { return }
        resolved = result; timeoutTask?.cancel(); timeoutTask = nil
        continuation?.resume(with: result); continuation = nil
    }
}

@MainActor
func callbackValue<Value>(timeout: UInt64 = 120,
    start: (@escaping (Result<Value, Error>) -> Void) -> Void) async throws -> Value {
    let gate = AsyncGate<Value>()
    return try await withTaskCancellationHandler(operation: {
        try Task.checkCancellation()
        return try await withCheckedThrowingContinuation { continuation in
            gate.attach(continuation, timeout: timeout)
            start { result in Task { @MainActor in gate.resolve(result) } }
        }
    }, onCancel: { Task { @MainActor in gate.resolve(.failure(CancellationError())) } })
}
