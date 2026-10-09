# Local JWT keys

Create `jwt-public.pem` and `jwt-private.pem` locally before starting the Gateway. Keep the private key out of version control. The Gateway reads the public key from `infrastructure/keys/jwt-public.pem`; Auth Service should use the matching private key to sign RS256 access tokens.
