#!/usr/bin/env python3
"""Check the scoped CA configuration and real TLS without using credentials."""
from pathlib import Path
import socket
import ssl
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
HOST = 'klient.gdansk.uw.gov.pl'
CA = ROOT / 'app/src/main/res/raw/certum_dv_tls_g2_r39.pem'


def main():
    config = ET.parse(ROOT / 'app/src/main/res/xml/network_security_config.xml').getroot()
    base = config.find('base-config')
    assert base.get('cleartextTrafficPermitted') == 'false'
    assert [c.get('src') for c in base.findall('trust-anchors/certificates')] == ['system']
    domains = config.findall('domain-config')
    assert len(domains) == 1
    domain = domains[0]
    hosts = domain.findall('domain')
    assert len(hosts) == 1 and hosts[0].text == HOST and hosts[0].get('includeSubdomains') == 'false'
    assert domain.get('cleartextTrafficPermitted') == 'false'
    assert [c.get('src') for c in domain.findall('trust-anchors/certificates')] == ['system', '@raw/certum_dv_tls_g2_r39']
    manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
    assert manifest.find('application').get('{http://schemas.android.com/apk/res/android}networkSecurityConfig') == '@xml/network_security_config'
    print('PASS: custom CA limited to exact portal hostname; HTTP prohibited')

    context = ssl.create_default_context(cafile=str(CA))
    context.verify_flags |= ssl.VERIFY_X509_PARTIAL_CHAIN
    assert context.verify_mode == ssl.CERT_REQUIRED and context.check_hostname
    with socket.create_connection((HOST, 443), timeout=20) as sock:
        with context.wrap_socket(sock, server_hostname=HOST) as tls:
            assert tls.getpeercert()
            leaf_der = tls.getpeercert(binary_form=True)
    print('PASS: portal TLS accepted with verified issuing CA')

    try:
        # Reach the same server, but require an unrelated certificate identity.
        with socket.create_connection((HOST, 443), timeout=20) as sock:
            with context.wrap_socket(sock, server_hostname='unrelated.invalid'):
                raise AssertionError('hostname verification was bypassed')
    except ssl.SSLCertVerificationError:
        print('PASS: certificate for wrong hostname rejected')

    future = subprocess.run([
        'openssl', 'verify', '-partial_chain', '-attime', '1830297600',
        '-CAfile', str(CA),
    ], input=ssl.DER_cert_to_PEM_cert(leaf_der), text=True, capture_output=True)
    assert future.returncode != 0 and 'certificate has expired' in future.stderr
    print('PASS: expired portal certificate rejected (simulated date: 2028-01-01)')


if __name__ == '__main__':
    main()
