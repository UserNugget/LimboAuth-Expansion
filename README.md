
# LimboAuth-Expansion

An a expansion for LimboAuth that provide placeholders for it.


# Placeholders

### Some information before we start

- All placeholders can be used as %\<placeholder>_username% to see that specific placeholder for specific player.
- All placeholders can be toggled in the config on LimboAuth side (backend-api.enabled-endpoints)

### Available Placeholders

- %limboauth_premium_state% - shows information about player premium status.
- %limboauth_hash% - shows player hashed password (disabled by default)
- %limboauth_totp_token% - shows player totp token (disabled by default)
- %limboauth_ip% - shows player ip address (disabled by default)
- %limboauth_login_ip% - shows player login ip address (disabled by default)
- %limboauth_uuid% - shows player uuid
- %limboauth_premium_uuid% - shows player premium uuid
- %limboauth_reg_date% - shows player registration date
- %limboauth_login_date% - shows player login date
- %limboauth_token_issued_at% - shows player token issued date