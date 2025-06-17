# Testing CardDAV URLs

## Test your URLs manually to verify they return collections:

### Test "Staff List" account:
```bash
curl -u "honorableswanobey:SilentExceptionalHawk4=#8+?!" \
  -X PROPFIND \
  -H "Content-Type: application/xml" \
  -H "Depth: 1" \
  --data '<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
  <D:prop>
    <D:resourcetype/>
    <D:displayname/>
    <C:addressbook-description/>
  </D:prop>
</D:propfind>' \
  https://dav.contactzilla.app/addressbooks/honorableswanobey/staff-list-1/
```

### Test "Clients" account:  
```bash
curl -u "flawlesshyenaecho:PhilanthropicDivineMoor17$%@+4" \
  -X PROPFIND \
  -H "Content-Type: application/xml" \
  -H "Depth: 1" \
  --data '<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:carddav">
  <D:prop>
    <D:resourcetype/>
    <D:displayname/>
    <C:addressbook-description/>
  </D:prop>
</D:propfind>' \
  https://dav.contactzilla.app/addressbooks/flawlesshyenaecho/clients-2/
```

### Expected Response:
You should get XML back showing addressbook collections. If you get 404 or empty results, the URLs need adjustment.

## Alternative URL Patterns to Try:

### Option 1: Root discovery
```kotlin
baseUrl = "https://dav.contactzilla.app/addressbooks/honorableswanobey/"
baseUrl = "https://dav.contactzilla.app/addressbooks/flawlesshyenaecho/"
```

### Option 2: Well-known URL
```kotlin  
baseUrl = "https://dav.contactzilla.app/.well-known/carddav"
```

### Option 3: User root
```kotlin
baseUrl = "https://dav.contactzilla.app/"
``` 