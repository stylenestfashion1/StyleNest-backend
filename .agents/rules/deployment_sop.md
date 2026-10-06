# StyleNest Production Deployment SOP & Architecture Rules

## Before Deploying Anything
Always confirm with the user first, explicitly, before running any deploy step — no exceptions, even if they seem to have already approved it earlier in the conversation. Deploying is a production action with real customer traffic behind it.

## Backend Deployment
1. Build locally:
   ```bash
   ./mvnw clean package -DskipTests
   ```
2. scp the jar to the server as a staged temp file (never overwrite the live one directly):
   ```bash
   scp target/stylenest_backend-0.0.1-SNAPSHOT.jar root@200.234.36.142:/opt/stylenest/app/stylenest_backend-0.0.1-SNAPSHOT.jar.new
   ```
3. SSH in and back up the currently-running jar first (never skip this, never delete old backups):
   ```bash
   cd /opt/stylenest/app
   cp stylenest_backend-0.0.1-SNAPSHOT.jar stylenest_backend-0.0.1-SNAPSHOT.jar.bak_$(date +%Y%m%d_%H%M%S)
   ```
4. Atomic swap into the exact filename systemd expects:
   ```bash
   mv stylenest_backend-0.0.1-SNAPSHOT.jar.new stylenest_backend-0.0.1-SNAPSHOT.jar
   ```
5. Restart:
   ```bash
   systemctl restart stylenest.service
   ```
6. Verify:
   ```bash
   systemctl is-active stylenest.service
   journalctl -u stylenest.service -n 100 --no-pager
   ```
   Look for `Started StyleNestBackendApplication` with zero errors/exceptions before considering it done.

### Secrets and Environment File
- Service name: `stylenest.service` (systemd).
- All secrets/config live in `/opt/stylenest/stylenest.env` (loaded via `EnvironmentFile=`).
- **NEVER** touch that file's contents as part of a code deploy.
- **NEVER** print its contents.

## Frontend Deployment
1. Build locally:
   ```bash
   npm run build
   ```
   Produces `dist/`.
2. Package and scp:
   ```bash
   tar -czf dist.tar.gz -C dist .
   scp dist.tar.gz root@200.234.36.142:/opt/stylenest/dist.tar.gz
   ```
3. SSH in, back up the current live content first:
   ```bash
   tar -czf /opt/stylenest/frontend_bak_$(date +%Y%m%d_%H%M%S).tar.gz -C /var/www/stylenest .
   ```
4. Extract the new build into `/var/www/stylenest/`, replacing everything there:
   ```bash
   tar -xzf /opt/stylenest/dist.tar.gz -C /var/www/stylenest/
   rm /opt/stylenest/dist.tar.gz
   ```
5. If building the tar on a Mac, clean AppleDouble files if needed:
   ```bash
   find /var/www/stylenest -name "._*" -delete
   ```
6. No restart needed — Nginx serves `/var/www/stylenest` directly as static files, SPA fallback via `try_files $uri $uri/ /index.html`.

## Architecture & Port Isolation
- **No reverse proxy** for the API on the frontend's own domain.
- Frontend calls `https://api.stylenestfashion.com` directly — baked into the build via `VITE_API_BASE_URL` in `.env` (`https://api.stylenestfashion.com/api`).
- Nginx terminates HTTPS for two separate domains:
  - `stylenestfashion.com` → static files (`/var/www/stylenest`)
  - `api.stylenestfashion.com` → `proxy_pass http://127.0.0.1:8081`
- **Port 8081** should only ever be bound to `127.0.0.1`, never exposed publicly (`SERVER_ADDRESS=127.0.0.1` in `stylenest.env`).
- **NEVER** curl a bare `localhost:PORT` without specifying the port correctly:
  - **Port 8080** is **Feminine21** (a different client).
  - **Port 8081** is **StyleNest**.
  - Mixing them up hits the wrong app entirely.
