Public issuing CA: Certum DV TLS G2 R39 CA
Source: https://certumdvtlsg2r39ca.repository.certum.pl/certumdvtlsg2r39ca.cer
Retrieved: 2026-10-02
SHA-256 certificate fingerprint:
83:C0:A5:A7:68:44:C8:40:DF:AF:82:0F:FD:02:AD:F6:57:3A:26:82:3E:F6:AF:75:8A:33:84:A0:AC:04:40:83

Verified with OpenSSL against the host system's public CA store.
Issuer: Certum Trusted Root CA
Basic constraints: critical CA:TRUE, pathlen:0
Validity: 2024-06-18 to 2039-06-05

The portal currently sends an unrelated old intermediate certificate.
Android Network Security Configuration trusts this issuing CA only for
the exact hostname klient.gdansk.uw.gov.pl. Other destinations retain
system CA trust only. Cleartext is prohibited. The app always cancels
onReceivedSslError; it does not use a trust-all manager or disable
hostname/validity verification.

This is an extra trust anchor, not an exemption for the leaf certificate.
System trust remains available when the portal corrects its chain or
changes its issuing CA. Remove the workaround after fixing the server.
