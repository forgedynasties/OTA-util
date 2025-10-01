# OTA API Usage Guide

This document provides information about the OTA server API endpoints and how to integrate them into your application for firmware update management.

## Table of Contents
1. [Overview](#overview)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Usage Examples](#usage-examples)
5. [Error Handling](#error-handling)
6. [Integration Flow](#integration-flow)

## Overview

The OTA server provides secure REST API endpoints for firmware update management:
- **Check for updates** - Device sends current build ID, receives update info
- **Validate checksums** - Device validates downloaded package integrity  
- **Download packages** - Device downloads firmware packages securely
- **Get build information** - Retrieve metadata about specific builds

**Base URL**: `http://10.32.1.11:8000`

## Authentication

All API endpoints require **Bearer Token authentication** using API keys.

### Generate API Key
1. Go to `http://10.32.1.11:8000/admin/api-keys`
2. Click "Generate New API Key"
3. Enter a name (e.g., "production-device")
4. Copy the generated API key

### Request Header Format
```
Authorization: Bearer YOUR_API_KEY_HERE
Content-Type: application/json
```

## API Endpoints

### 1. Check for Updates
**POST** `/api/check-update`

Device sends its current build ID to check for available updates.

**Request:**
```bash
curl -X POST "http://10.32.1.11:8000/api/check-update" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"build_id": "build-1001"}'
```

**Request Body:**
```json
{
  "build_id": "build-1001"
}
```

**Response (Update Available):**
```json
{
  "status": "update-available",
  "package_url": "/packages/ota-build-1002.zip",
  "build_id": "build-1002",
  "patch_notes": "New features and security updates"
}
```

**Response (Up to Date):**
```json
{
  "status": "up-to-date"
}
```

### 2. Validate Package Checksum
**POST** `/api/validate-checksum`

Device sends build ID and checksum to verify package integrity.

**Request:**
```bash
curl -X POST "http://10.32.1.11:8000/api/validate-checksum" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"build_id": "build-1002", "checksum": "a487654c7bb3c984..."}'
```

**Request Body:**
```json
{
  "build_id": "build-1002",
  "checksum": "a487654c7bb3c984177a863bd5ba6f35fa1eb7f1b56155fa593333f8c78c2ad0"
}
```

**Response:**
```json
{
  "status": "success",
  "is_valid": true,
  "message": "Checksum valid"
}
```

### 3. Download Package
**GET** `/packages/{filename}`

Download the actual firmware package file.

**Request:**
```bash
curl -X GET "http://10.32.1.11:8000/packages/ota-build-1002.zip" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -o "firmware_update.zip"
```

### 4. Get All Builds (Optional)
**GET** `/api/builds`

Retrieve information about all available builds.

**Request:**
```bash
curl -X GET "http://10.32.1.11:8000/api/builds" \
     -H "Authorization: Bearer YOUR_API_KEY"
```

**Response:**
```json
{
  "build-1001": {
    "build_id": "build-1001",
    "ota_package": {
      "timestamp": "2025-10-01T14:16:04.406035",
      "package_url": "/packages/ota-build-1001.zip",
      "checksum": "a487654c7bb3c984177a863bd5ba6f35fa1eb7f1b56155fa593333f8c78c2ad0",
      "patch_notes": "Update to version 1.1.0"
    }
  }
}
```

### 5. Get Specific Build (Optional)
**GET** `/api/builds/{build_id}`

Get information about a specific build.

**Request:**
```bash
curl -X GET "http://10.32.1.11:8000/api/builds/build-1001" \
     -H "Authorization: Bearer YOUR_API_KEY"
```



## Usage Examples

### 1. Check for Updates (HTTP Request)

**Using curl:**
```bash
# Check if device build-1001 has updates available
curl -X POST "http://10.32.1.11:8000/api/check-update" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"build_id": "build-1001"}'
```

**Using JavaScript/Node.js:**
```javascript
const checkForUpdates = async (buildId, apiKey) => {
  const response = await fetch('http://10.32.1.11:8000/api/check-update', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ build_id: buildId })
  });
  
  return await response.json();
};

// Usage
const result = await checkForUpdates('build-1001', 'your-api-key');
console.log(result);
```

**Using Python:**
```python
import requests

def check_for_updates(build_id, api_key):
    headers = {
        'Authorization': f'Bearer {api_key}',
        'Content-Type': 'application/json'
    }
    data = {'build_id': build_id}
    
    response = requests.post(
        'http://10.32.1.11:8000/api/check-update',
        headers=headers,
        json=data
    )
    
    return response.json()

# Usage
result = check_for_updates('build-1001', 'your-api-key')
print(result)
```

### 2. Download Update Package

**Using curl:**
```bash
# Download the update package
curl -X GET "http://10.32.1.11:8000/packages/ota-build-1002.zip" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -o "firmware_update.zip"
```

### 3. Validate Checksum

**Calculate checksum and validate:**
```bash
# Calculate SHA256 checksum of downloaded file
sha256sum firmware_update.zip

# Validate with server
curl -X POST "http://10.32.1.11:8000/api/validate-checksum" \
     -H "Authorization: Bearer YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"build_id": "build-1002", "checksum": "calculated-checksum-here"}'
```

## Error Handling

### HTTP Status Codes

| Status Code | Meaning | Action |
|-------------|---------|---------|
| `200` | Success | Process response data |
| `401` | Unauthorized | Check API key validity |
| `404` | Not Found | Build ID doesn't exist on server |
| `500` | Server Error | Retry later or contact support |

### Error Response Format

```json
{
  "status": "error",
  "message": "Build ID not found"
}
```

### Common Error Scenarios

1. **Authentication Error (401)**
   ```json
   {
     "detail": "Invalid API key"
   }
   ```
   **Solution**: Verify your API key is correct and properly formatted

2. **Build Not Found (404)**
   ```json
   {
     "status": "error", 
     "message": "Build ID not found"
   }
   ```
   **Solution**: Ensure the build ID exists in the server database

3. **Network Connectivity**
   - **Solution**: Check internet connection and server accessibility

## Integration Flow

### Typical OTA Update Process

1. **Device Startup/Periodic Check**
   ```
   Device → POST /api/check-update {"build_id": "current-build"}
   Server → Response with update info or "up-to-date"
   ```

2. **If Update Available**
   ```
   Device → GET /packages/ota-new-build.zip (download package)
   Device → Calculate SHA256 checksum of downloaded file
   Device → POST /api/validate-checksum {"build_id": "new-build", "checksum": "calculated-hash"}
   Server → {"status": "success", "is_valid": true}
   ```

3. **Install Update**
   ```
   Device → Apply firmware update using downloaded package
   Device → Reboot with new build ID
   ```

### Sample Integration Steps

1. **Store your device's current build ID**
   - Version number, build timestamp, or unique identifier
   - Example: `"device-v1.2.3"`, `"build-20251001"`

2. **Implement update check logic**
   - Call `/api/check-update` with current build ID
   - Handle "up-to-date" vs "update-available" responses

3. **Download and validate packages**
   - Download from `package_url` in response
   - Calculate SHA256 checksum of downloaded file
   - Validate checksum with `/api/validate-checksum`

4. **Apply update**
   - Use your device's update mechanism (recovery mode, bootloader, etc.)
   - Update stored build ID after successful installation

### Security Considerations

- **Always validate checksums** before applying updates
- **Use HTTPS** in production environments
- **Store API keys securely** (encrypted storage, environment variables)
- **Implement retry logic** with exponential backoff
- **Log update attempts** for debugging and monitoring

### Rate Limiting

- Implement reasonable check intervals (e.g., once per hour/day)
- Don't spam the server with frequent update checks
- Consider user-initiated vs automatic checks

### Testing Your Integration

1. **Test with curl** commands to verify API responses
2. **Run the provided test script**: `python test_api.py`
3. **Check server logs** for any error messages
4. **Verify file downloads** complete successfully
5. **Test checksum validation** with both valid and invalid checksums