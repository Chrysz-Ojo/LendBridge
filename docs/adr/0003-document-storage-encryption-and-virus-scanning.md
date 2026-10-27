# ADR 0003: Document storage encryption and virus-scanning posture

Date: 2026-10-27

## Status

Accepted

## Context

The PRD requires uploaded documents to be "virus-scanned and stored
encrypted" (E2-S3), with "server-side encryption and ClamAV scanning" named
as the intended mechanism for files and documents specifically — distinct
from the field-level encryption (`FieldCipher`, AES-256-GCM with an
application-held key) used for PII columns in other services. Document
Service's first implementation ran the virus scan but stored the file,
persisted the DB row, and published `document.uploaded` regardless of the
scan result, and set no encryption on the S3 `PutObject` call at all —
neither requirement was actually enforced.

## Decision

- **Virus scanning**: synchronous, blocking the upload request. Documents
  in scope are capped at 10 MB, so a scan is fast enough not to need an
  async/quarantine flow. `VirusScanner` is a port (per CLAUDE.md's
  external-provider rule); `FakeVirusScanner` is the local/test
  implementation and flags the standard EICAR antivirus test string as
  infected, mirroring real AV engine behavior. An infected result now
  throws before the object is stored, the DB row is saved, or
  `document.uploaded` is published — nothing persists for a rejected
  upload.
- **Encryption at rest**: SSE-S3 (`ServerSideEncryption.AES256`), set per
  object on `PutObjectRequest`, rather than SSE-KMS with a customer-managed
  key per document. SSE-S3 is S3-managed, requires no key provisioning or
  rotation policy of our own, and satisfies "server-side encryption" as
  stated in the PRD without introducing KMS as a new dependency for this
  service.

## Consequences

- **Known v1 gap**: `FakeVirusScanner` is not a real antivirus engine — no
  ClamAV (or equivalent) is wired up yet. The EICAR-detection behavior
  makes the rejection path testable, but a genuine malicious file would
  pass today. Revisit before handling real user uploads in production,
  per CLAUDE.md's external-provider rule (fake implementation now, real
  adapter behind the same `VirusScanner` interface later).
- SSE-S3 gives no per-document key control, no customer-managed rotation,
  and no CloudTrail-visible key-usage audit trail the way SSE-KMS would.
  If a future compliance requirement needs that (e.g. per-tenant keys, key
  revocation), this should be revisited as a deliberate upgrade, not
  assumed to already exist.
- No encryption is applied to documents already stored before this
  decision (if any) — SSE-S3 only affects objects written after this
  change; a bucket-level default-encryption policy was deliberately not
  configured, since the application always sets the header explicitly.
