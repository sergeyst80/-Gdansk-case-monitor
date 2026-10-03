import Foundation
import WebKit
import Security

@MainActor
final class PortalSession: NSObject, WKNavigationDelegate {
    static let host = "klient.gdansk.uw.gov.pl"
    private var webView: WKWebView?
    private var navigationError: String?
    var progress: (String) -> Void = { _ in }
    private let adapter: String

    override init() {
        let url = Bundle.main.url(forResource: "portal_adapter", withExtension: "js")
        adapter = url.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
        super.init()
    }
    static func trusted(_ url: URL) -> Bool {
        url.scheme?.lowercased() == "https" && url.host?.lowercased() == host
            && (url.port == nil || url.port == 443)
            && url.user == nil && url.password == nil
    }
    // WebKit's public content-rule API: block foreign subresources, not only navigation.
    // No private API or URLProtocol interception of HTTPS traffic is used.
    private static let resourceRules = #"[{"trigger":{"url-filter":".*"},"action":{"type":"block"}},{"trigger":{"url-filter":"^(https|wss)://klient\\.gdansk\\.uw\\.gov\\.pl(:443)?/","url-filter-is-case-sensitive":false},"action":{"type":"ignore-previous-rules"}}]"#
    private func quoted(_ value: String) throws -> String {
        let data = try JSONEncoder().encode(value)
        return String(decoding: data, as: UTF8.self)
    }
    func check(_ account: Account) async throws -> Snapshot {
        guard !adapter.isEmpty else { throw MonitorError.message("adapter_error") }
        guard webView == nil else { throw MonitorError.message("portal_busy") }
        progress("action_start"); navigationError = nil
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent() // A fresh, isolated session for every account.
        let rules: WKContentRuleList = try await callbackValue(timeout: 15) { completion in
            WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "PortalOriginOnly-v1",
                encodedContentRuleList: Self.resourceRules) { rules, error in
                if let rules { completion(.success(rules)) }
                else { completion(.failure(MonitorError.message("adapter_error"))) }
            }
        }
        try Task.checkCancellation()
        config.userContentController.add(rules)
        let view = WKWebView(frame: CGRect(x: 0, y: 0, width: 1080, height: 1920), configuration: config)
        view.navigationDelegate = self; webView = view
        defer { view.stopLoading(); view.navigationDelegate = nil; webView = nil }
        progress("action_connect")
        view.load(URLRequest(url: URL(string: "https://" + Self.host + "/")!, cachePolicy: .reloadIgnoringLocalCacheData))
        var submitted = false
        var deadline = Date().addingTimeInterval(90)
        while Date() < deadline {
            try Task.checkCancellation()
            if let navigationError { throw MonitorError.message(navigationError) }
            if let url = view.url, Self.trusted(url) {
                let js = "(" + adapter + ")(" + (try quoted(submitted ? "read" : "login")) + ","
                    + (try quoted(submitted ? "" : account.login)) + ","
                    + (try quoted(submitted ? "" : account.password)) + ")"
                let value: Any = try await callbackValue(timeout: 15) { completion in
                    view.evaluateJavaScript(js) { value, error in
                        if let error { completion(.failure(error)) }
                        else { completion(.success(value ?? NSNull())) }
                    }
                }
                if let text = value as? String, let data = text.data(using: .utf8),
                   let result = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let state = result["state"] as? String {
                    switch state {
                    case "SUBMITTED": submitted = true; deadline = Date().addingTimeInterval(60); progress("action_read")
                    case "READY" where submitted:
                        guard let json = result["snapshot"], JSONSerialization.isValidJSONObject(json) else {
                            throw MonitorError.message("unrecognized")
                        }
                        let snapshot = try JSONDecoder().decode(Snapshot.self, from: JSONSerialization.data(withJSONObject: json))
                        guard !snapshot.fields.isEmpty else { throw MonitorError.message("unrecognized") }
                        return snapshot
                    case "CHALLENGE": throw MonitorError.message("challenge_error")
                    case "REJECTED": throw MonitorError.message("rejected_error")
                    case "WAIT_FORM", "LOGIN": progress("action_login")
                    default: progress(submitted ? "action_read" : "action_load")
                    }
                }
            }
            try await Task.sleep(nanoseconds: submitted ? 1_200_000_000 : 700_000_000)
        }
        throw MonitorError.message("timeout_error")
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url, Self.trusted(url) else {
            if action.targetFrame == nil || action.targetFrame?.isMainFrame == true { navigationError = "redirect_error" }
            decisionHandler(.cancel); return
        }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if response.isForMainFrame, let http = response.response as? HTTPURLResponse, http.statusCode >= 400 {
            navigationError = "portal_http_error"; decisionHandler(.cancel); return
        }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) { progress("action_load") }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { record(error) }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { record(error) }
    private func record(_ error: Error) {
        let code = (error as NSError).code
        if code == NSURLErrorCancelled { return }
        navigationError = (-1206 ... -1200).contains(code) ? "portal_ssl_error" : "portal_network_error"
    }

    // Add the verified public CA only for this portal. NEVER accept a failed trust evaluation.
    func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge,
                 completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              challenge.protectionSpace.host.lowercased() == Self.host,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil); return
        }
        let policy = SecPolicyCreateSSL(true, Self.host as CFString)
        guard SecTrustSetPolicies(trust, policy) == errSecSuccess else {
            navigationError = "portal_ssl_error"; completionHandler(.cancelAuthenticationChallenge, nil); return
        }
        if let url = Bundle.main.url(forResource: "certum_dv_tls_g2_r39", withExtension: "pem"),
           let pem = try? String(contentsOf: url, encoding: .utf8) {
            let base64 = pem.components(separatedBy: .newlines).filter { !$0.hasPrefix("-----") }.joined()
            if let data = Data(base64Encoded: base64), let ca = SecCertificateCreateWithData(nil, data as CFData) {
                guard SecTrustSetAnchorCertificates(trust, [ca] as CFArray) == errSecSuccess,
                      SecTrustSetAnchorCertificatesOnly(trust, false) == errSecSuccess else {
                    navigationError = "portal_ssl_error"; completionHandler(.cancelAuthenticationChallenge, nil); return
                }
            }
        }
        if SecTrustEvaluateWithError(trust, nil) { completionHandler(.useCredential, URLCredential(trust: trust)) }
        else { navigationError = "portal_ssl_error"; completionHandler(.cancelAuthenticationChallenge, nil) }
    }
}
