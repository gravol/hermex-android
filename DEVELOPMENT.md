# Hermex Android — Development Notes

## Certificate Pinning

The app uses OkHttp `CertificatePinner` to trust the self-signed cert on the Hermes server. The pin is a SHA-256 hash of the server's public key.

**Location:** `core/network/src/main/java/com/hermex/core/network/ApiClient.kt`

**Current pin:** `sha256/jOIJfSaEOx0W1RLGrwSG/gIH4c2I5Nz2y193EoWi2+Q=` for `100.80.204.66`

### When to update the pin

The pin must be regenerated when:

1. **The server's TLS certificate is regenerated** (new self-signed cert, new CA)
2. **The Tailscale IP changes** (server restart causes a new 100.x address)

### How to regenerate

```bash
# Replace 100.80.204.66 with the current server IP
PIN=$(openssl s_client -connect 100.80.204.66:8443 </dev/null 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | base64)
echo "sha256/$PIN"
```

Then update the `CertificatePinner.Builder().add(...)` call in `ApiClient.kt` and rebuild.
