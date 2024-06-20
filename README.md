
# LimboAuth-Expansion

An a expansion for LimboAuth that provide placeholders for it.


# Placeholders

### Some information before we start

- All placeholders require at least one player on the server to work.
- All placeholders can be used as **%limboauth\_\<placeholder>\_\<username>%** to see that specific placeholder for specific player.
- All placeholders can be toggled in the config on LimboAuth side (backend-api.enabled-endpoints)

### Available Placeholders

- **%limboauth_premium_state%** - shows information about player premium status.
- **%limboauth_hash%** - shows player hashed password (disabled by default)
- **%limboauth_totp_token%** - shows player totp token (disabled by default)
- **%limboauth_ip%** - shows player ip address (disabled by default)
- **%limboauth_login_ip%** - shows player login ip address (disabled by default)
- **%limboauth_uuid%** - shows player uuid
- **%limboauth_premium_uuid%** - shows player premium uuid
- **%limboauth_reg_date%** - shows player registration date
- **%limboauth_login_date%** - shows player login date
- **%limboauth_token_issued_at%** - shows player token issued date

### Config options

- **premium** - message for premium accounts
- **cracked** - message for cracked accounts
- **unknown** - message for unknown account/parameter
- **error** - message then LimboAuth fails to get player data
- **requesting** - message then expansion is waiting for LimboAuth to respond
- **purge_cache_millis** - cache updates in milliseconds {:
- **request_timeout** - maximum timeout for waiting for LimboAuth to respond
- **enable_prefetch** - prefetches available data from LimboAuth to reduce **requesting** messages