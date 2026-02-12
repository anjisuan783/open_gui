# Changelog

All notable changes to the OpenCode Android WebView Voice Assistant project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - 2024-02-12

#### File Upload Support
- **WebView File Upload**: Added `onShowFileChooser` callback in WebChromeClient to support native file selection
- **Storage Permissions**: Added `READ_EXTERNAL_STORAGE` and `READ_MEDIA_IMAGES` permissions to AndroidManifest.xml
- **Attachment Button**: Added attachment button (📎) in the bottom UI layout for quick file access
- **File Type Support**: Support for images (PNG, JPEG, GIF, WebP) and PDF files
- **Chunked Transfer**: Implemented chunked Base64 data transfer (30KB chunks) for large files as fallback method

#### UI Enhancements
- **Bottom Layout Redesign**: Reorganized bottom interaction area with attachment button on the left
- **Toast Notifications**: Added user feedback for file processing status

#### Technical Implementation
- **WebChromeClient.onShowFileChooser**: Handles file picker requests from WebView
- **JavaScriptInterface Extension**: Added `onAttachmentReady` callback for attachment status
- **File Size Handling**: Supports files up to 10MB with appropriate error handling
- **Permission Management**: Dynamic permission checking for storage access

### Known Issues
- Android bottom attachment button uses chunked Base64 transfer which may not work with all web interfaces
- Recommended to use OpenCode's native file selection button for best compatibility

## [Previous Versions]

### Base Version
- Initial Android WebView Voice Assistant implementation
- WebView integration with OpenCode web interface
- Voice recording and transcription functionality
- HTTP authentication support
- Keyboard visibility detection

---

## Development Notes

### 2024-02-12: File Upload Implementation

**Implementation Strategy**: 
1. Primary: WebView's native `onShowFileChooser` API (works perfectly)
2. Fallback: Chunked Base64 transfer via JavaScript injection (experimental)

**Key Technical Decisions**:
- Used standard WebView file upload mechanism instead of clipboard simulation
- Implemented chunked transfer for large files to avoid WebView JavaScript length limitations
- Kept Android attachment button as secondary option while recommending web-native button

**Testing Results**:
- ✅ Large file support (tested with 3.8MB images)
- ✅ Image preview display in OpenCode
- ✅ Successful upload to various AI models
- ⚠️ Some models don't support image analysis (model limitation, not implementation issue)
