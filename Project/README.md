# WordVault — Local Word Document Search

WordVault is a local-first Java website for saving Microsoft Word `.doc` and `.docx` documents on your computer and searching their contents.

## Website sections
- **Overview:** document count, local storage total, and recently added files.
- **Documents:** upload multiple Word files, drag and drop, filter by filename, download originals, and delete saved files.
- **Search:** keyword, exact phrase, spelling help, and related-document search.

## Backend behavior
- Original Word files are saved in `data/documents/` and remain there across server restarts until deleted in the Documents page.
- Apache POI extracts document text when a file is uploaded. The in-memory inverted index is rebuilt from the saved Word files at startup.
- The index implements keyword term-frequency ranking, KMP exact-phrase matching, edit-distance spelling corrections, and cosine-similarity related-document ranking.
- Uploads are limited to `.doc` and `.docx`, with a 50 MB maximum per file. The app binds to `localhost` for local use.
- APIs: `GET /api/health`, `GET /api/documents`, `POST /api/upload?name=file.docx`, `GET /api/documents/{id}`, `DELETE /api/documents/{id}`, `GET /api/search?q=words&mode=keyword|phrase|spelling|related`.

## Requirements
- Java JDK 17 or newer
- Maven 3.8 or newer
- Internet access the first time Maven downloads Apache POI and its dependencies

## Run in VS Code on Windows
1. Open this extracted folder in VS Code (the folder containing `pom.xml`).
2. Choose **Terminal → New Terminal**.
3. Run `mvn compile exec:java`.
4. Wait until the terminal reports `WordVault is running at http://localhost:8080` and open that address in a browser on the same computer.

You can also double-click `run.bat`. Keep the server terminal open while using the site. Press Ctrl+C to stop it.

## Storage and backup
The original files are stored locally in `data/documents/`. Deleting a file in the website removes its saved original. The project does not provide cloud storage, automatic backup, encryption, or user accounts. Back up `data/documents/` separately to protect against device or drive failure.
