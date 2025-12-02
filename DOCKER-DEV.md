# Docker Development Setup

This guide explains how to use Docker for hot-reloading development.

## Quick Start

```bash
# Build and start the development container
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f
```

The application will be available at http://localhost:10010

## How it Works

- **Hot Reloading**: Changes to `src/`, `public/`, `translations/`, and `bin/` directories are automatically reflected in the running container
- **No Rebuild Needed**: Once built, you can start/stop the container without rebuilding unless you change `package.json` or other dependencies

## Useful Commands

```bash
# Stop the container
docker-compose down

# Restart the container
docker-compose restart

# View logs
docker-compose logs -f roshar-map-dev

# Access container shell
docker-compose exec roshar-map-dev sh

# Rebuild if dependencies changed
docker-compose up --build
```

## When to Rebuild

You need to rebuild the Docker image when:
- You modify `package.json` or `yarn.lock`
- You change any configuration files (babel.config.js, vue.config.js, etc.)
- You update `.yarn/` directory

For all other changes (components, styles, translations), hot-reload handles it automatically.

## Troubleshooting

**Port already in use:**
```bash
# Check what's using port 10010
# Windows:
netstat -ano | findstr :10010
# Linux/Mac:
lsof -i :10010

# Kill the process or change the port in docker-compose.yml
```

**Changes not reflecting:**
- Check that the volume mounts are working: `docker-compose exec roshar-map-dev ls /app/src`
- Restart the container: `docker-compose restart`
- Check logs for errors: `docker-compose logs -f`

**Build fails:**
- Clear Docker cache: `docker-compose build --no-cache`
- Check that you have enough disk space

