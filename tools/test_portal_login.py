#!/usr/bin/env python3
"""Local login smoke test. Never print credentials, HTML, or case details.

Dependency: websocket-client. Uses Chromium and a disposable local profile.
"""
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.request
import websocket

ROOT = Path(__file__).resolve().parents[1]
PORTAL = 'https://klient.gdansk.uw.gov.pl/'


def main():
    credentials = {}
    for line in (ROOT / '.secrets/portal-test.env').read_text().splitlines():
        if line.startswith(('PORTAL_LOGIN=', 'PORTAL_PASSWORD=')):
            key, value = line.split('=', 1)
            credentials[key] = value
    login = credentials.get('PORTAL_LOGIN', '')
    password = credentials.get('PORTAL_PASSWORD', '')
    if not login or not password:
        print('TEST_NOT_RUN: fill both fields in .secrets/portal-test.env')
        return 2
    adapter = (ROOT / 'app/src/main/assets/portal_adapter.js').read_text()
    profile = tempfile.mkdtemp(prefix='gdansk-portal-test-')
    browser = None
    ws = None
    try:
        browser = subprocess.Popen([
            shutil.which('chromium') or 'chromium', '--headless=new', '--no-sandbox',
            '--disable-dev-shm-usage', '--disable-extensions', '--disable-sync',
            '--no-first-run', '--no-default-browser-check', '--log-level=3',
            '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0',
            '--window-size=1080,1920', '--user-data-dir=' + profile, 'about:blank',
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        end = time.monotonic() + 20
        port_file = Path(profile) / 'DevToolsActivePort'
        while not port_file.exists():
            if browser.poll() is not None or time.monotonic() >= end:
                print('TEST_FAILED: browser_start'); return 3
            time.sleep(0.2)
        port = int(port_file.read_text().splitlines()[0])
        # Bypass proxy environment variables for the loopback-only debugger.
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(f'http://127.0.0.1:{port}/json/list', timeout=10) as response:
            targets = json.load(response)
        target = next(t for t in targets if t['type'] == 'page')
        ws = websocket.create_connection(target['webSocketDebuggerUrl'], timeout=15, suppress_origin=True, http_proxy_host=None)
        sequence = 0

        def rpc(method, params):
            nonlocal sequence
            sequence += 1
            ws.send(json.dumps({'id':sequence, 'method':method, 'params':params}))
            while True:
                message = json.loads(ws.recv())
                if message.get('id') == sequence:
                    if 'error' in message: raise RuntimeError('debugger_rpc')
                    return message.get('result', {})

        def evaluate(expression):
            response = rpc('Runtime.evaluate', {'expression':expression, 'returnByValue':True})
            if 'exceptionDetails' in response: raise RuntimeError('adapter_script')
            return response.get('result', {}).get('value')

        rpc('Page.navigate', {'url':PORTAL})
        submitted = False
        deadline = time.monotonic() + 90
        last_state = None
        while time.monotonic() < deadline:
            current_url = evaluate('location.href') or ''
            if current_url == 'about:blank':
                time.sleep(0.5); continue
            from urllib.parse import urlsplit
            current = urlsplit(current_url)
            if current.scheme != 'https' or current.hostname != 'klient.gdansk.uw.gov.pl':
                print('TEST_FAILED: unexpected_navigation'); return 4
            mode = 'read' if submitted else 'login'
            args = [mode, '' if submitted else login, '' if submitted else password]
            result = json.loads(evaluate('(' + adapter + ')(' + ','.join(json.dumps(a) for a in args) + ')'))
            state = result.get('state', 'UNKNOWN')
            if state != last_state:
                print('STAGE: ' + state, flush=True)
                last_state = state
            if state == 'SUBMITTED':
                submitted = True; deadline = time.monotonic() + 60
            elif state == 'READY' and submitted:
                count = len(result.get('snapshot', {}).get('fields', []))
                if '--check-labels' in sys.argv:
                    allowed = {'name', 'caseNumber', 'filedDate', 'stage', 'stageDescription', 'notes', 'documents'}
                    keys = [f.get('key') if f.get('key') in allowed else 'unmapped' for f in result['snapshot']['fields']]
                    print('FIELD_KEYS: ' + ','.join(keys))
                    expected = ['name', 'caseNumber', 'filedDate', 'stage', 'stageDescription']
                    if 'notes' in keys: expected.append('notes')
                    expected.append('documents')
                    if keys != expected:
                        print('TEST_FAILED: field_labels'); return 8
                if '--inspect-labels' in sys.argv:
                    # Structural metadata only: never return field values or arbitrary text.
                    structure = evaluate("""JSON.stringify(Array.from(document.querySelectorAll('vaadin-text-field,vaadin-text-area,vaadin-date-picker')).map(e => {
                        const parents=[]; let node=e;
                        for(let i=0;i<3 && node;i++,node=node.parentElement) {
                            const prev=node.previousElementSibling;
                            parents.push({tag:node.tagName,previous:prev?prev.tagName:null,previousChildren:prev?Array.from(prev.children).map(c=>c.tagName):[],children:node.parentElement?Array.from(node.parentElement.children).map(c=>c.tagName):[]});
                        }
                        return {tag:e.tagName,hasOwnLabel:!!e.label,parents};
                    }))""")
                    print('LABEL_STRUCTURE: ' + structure)
                print('TEST_PASSED: login_and_extract; field_count=' + str(count))
                return 0
            elif state in ('REJECTED', 'CHALLENGE'):
                print('TEST_FAILED: ' + state); return 5
            time.sleep(1.2 if submitted else 0.7)
        print('TEST_FAILED: timeout; stage=' + str(last_state)); return 6
    except Exception as error:
        # Do not include exception messages, URLs, browser state, or stack traces.
        print('TEST_FAILED: ' + type(error).__name__); return 7
    finally:
        if ws is not None:
            try: ws.close()
            except Exception: pass
        if browser is not None:
            # Chromium subprocesses can outlive its main process and write cache files.
            try: os.killpg(browser.pid, signal.SIGTERM)
            except ProcessLookupError: pass
            try: browser.wait(timeout=5)
            except subprocess.TimeoutExpired:
                pass
            try: os.killpg(browser.pid, signal.SIGKILL)
            except ProcessLookupError: pass
            browser.wait()
        for attempt in range(10):
            try:
                shutil.rmtree(profile)
                break
            except FileNotFoundError:
                break
            except OSError:
                if attempt == 9:
                    print('CLEANUP_WARNING: temporary browser profile requires removal')
                else:
                    time.sleep(0.2)


if __name__ == '__main__':
    raise SystemExit(main())
