# CATVC-InScan

An advanced vehicle barcode scanning and inventory tracking application designed for seamless barcode scanning, automated decoding fallbacks, machine learning integration, and document preparation.

## Features

- **Multi-Engine Barcode Scanning**: Uses your mobile phone's camera to capture barcodes.
  - **Primary Engine**: Google ML Kit with vPic (NHTSA Product Information Catalog) API integration.
  - **Silent Fallbacks**: If ML Kit and vPic cannot read or decode a barcode, the app silently and automatically falls back to **zbar.wasm** or **ZXing** to ensure a successful scan.
- **Foreign Barcode & Decryption ML**: vPic is a governmental API that can scan foreign barcodes but cannot decrypt/decode them directly. The app integrates local machine learning:
  - If you manually fill in the vehicle information correctly, the app will learn, remember, and auto-populate that data the next time that barcode is scanned.
- **Manual "Scan" Entry**: If a barcode fails to scan entirely or the camera struggles, you can always enter a manual item "scan" input.
- **Inventory Management**: Keeps a complete inventory of everything scanned on a dedicated, separate screen.
- **Document Preparation & Generation**: A final document-preparing screen allowing you to work with sales documents, terms of service (ToS), DMV forms, etc.
  - Upload your own documents, start from a template, or generate documents directly in-app.
  - Automatically populates the document with data from the currently selected vehicle.
  - Includes a built-in DocuSign-style electronic signature feature.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
