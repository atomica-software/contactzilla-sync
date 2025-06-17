# URL Alternatives for CardDAV Service Discovery

Try these URL patterns in order if the current ones don't work:

## Option 1: Well-known URLs (Current)
```kotlin
baseUrl = "https://dav.contactzilla.app/.well-known/carddav"
```

## Option 2: Root URL Discovery  
```kotlin
baseUrl = "https://dav.contactzilla.app/"
```

## Option 3: Direct Addressbook Parent
```kotlin
baseUrl = "https://dav.contactzilla.app/addressbooks/honorableswanobey/"
baseUrl = "https://dav.contactzilla.app/addressbooks/flawlesshyenaecho/"
```

## Option 4: Specific Addressbook URLs
```kotlin
baseUrl = "https://dav.contactzilla.app/addressbooks/honorableswanobey/staff-list-1/"
baseUrl = "https://dav.contactzilla.app/addressbooks/flawlesshyenaecho/clients-2/"
```

## Option 5: Alternative Server Paths
```kotlin
baseUrl = "https://dav.contactzilla.app/carddav/"
baseUrl = "https://dav.contactzilla.app/dav/"
```

## Manual Testing Commands

Test each URL pattern manually:

```bash
# Test well-known
curl -u "honorableswanobey:SilentExceptionalHawk4=#8+?!" \
  -X PROPFIND \
  -H "Content-Type: application/xml" \
  -H "Depth: 0" \
  https://dav.contactzilla.app/.well-known/carddav

# Test root  
curl -u "honorableswanobey:SilentExceptionalHawk4=#8+?!" \
  -X PROPFIND \
  -H "Content-Type: application/xml" \
  -H "Depth: 1" \
  https://dav.contactzilla.app/

# Test addressbooks parent
curl -u "honorableswanobey:SilentExceptionalHawk4=#8+?!" \
  -X PROPFIND \
  -H "Content-Type: application/xml" \
  -H "Depth: 1" \
  https://dav.contactzilla.app/addressbooks/honorableswanobey/
```

Look for responses that include:
- `<D:resourcetype><C:addressbook/></D:resourcetype>` 
- Collections with displaynames
- Principal URLs 