# MDM Email-Based Configuration Format

## Overview

The MDM (Mobile Device Management) configuration uses **email/password** authentication with automatic URL generation. All configuration keys require numbered suffixes starting from `_1`.

## JSON Configuration Format

### Single Account Configuration

```json
{
  "managedProperty": [
    {
      "key": "login_email_1",
      "valueString": "user@contactzilla.app"
    },
    {
      "key": "login_password_1", 
      "valueString": "userPassword123"
    },
    {
      "key": "login_account_name_1",
      "valueString": "My Account"
    },
    {
      "key": "organization",
      "valueString": "Acme Corporation"
    },
    {
      "key": "managed_by",
      "valueString": "Acme Corporation IT Department"
    }
  ]
}
```

### Multiple Accounts

For multiple accounts, continue the numbering sequence:

```json
{
  "managedProperty": [
    {
      "key": "login_email_1",
      "valueString": "staff@contactzilla.app"
    },
    {
      "key": "login_password_1",
      "valueString": "staffPassword123"
    },
    {
      "key": "login_account_name_1", 
      "valueString": "Staff List"
    },
    {
      "key": "login_email_2",
      "valueString": "clients@contactzilla.app"
    },
    {
      "key": "login_password_2",
      "valueString": "clientsPassword456"
    },
    {
      "key": "login_account_name_2",
      "valueString": "Clients"
    }
  ]
}
```

## Configuration Keys

### Required Keys
- `login_email_N` - Email address (must end with `@contactzilla.app`) where N is the account number starting from 1
- `login_password_N` - Password for authentication

### Optional Keys
- `login_account_name_N` - Display name for the account (defaults to username part of email)
- `organization` - Organization name
- `managed_by` - Entity managing the account settings

### Key Numbering System
All account keys require number suffixes starting from `_1`:
- First account: `login_email_1`, `login_password_1`, `login_account_name_1`
- Second account: `login_email_2`, `login_password_2`, `login_account_name_2`
- Third account: `login_email_3`, `login_password_3`, `login_account_name_3`
- And so on...

## URL Generation

The base URL is automatically generated from the email address:
- Email: `user@contactzilla.app` 
- Generated URL: `https://dav.contactzilla.app/addressbooks/user/`



## Debug Configurations

In debug builds, the following test accounts are automatically available:

1. **Staff List**
   - Email: `honorableswanobey@contactzilla.app`
   - Password: `SilentExceptionalHawk4=#8+?!`

2. **Clients**
   - Email: `flawlesshyenaecho@contactzilla.app`
   - Password: `PhilanthropicDivineMoor17$%@+4`

3. **BackToTheFuture**
   - Email: `hypnoticmonasterysustain@contactzilla.app`
   - Password: `MagicDeepOwl9%54$=@`

## Implementation Notes

- Email addresses must end with `@contactzilla.app` due to domain validation
- The system automatically derives the correct CardDAV URL from the email domain
- Account names are auto-generated from the username part of email if not specified
- Multiple accounts are supported using numbered suffixes (_1, _2, _3, etc.) 