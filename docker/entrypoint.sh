#!/usr/bin/env bash
set -e

cd /var/www/html

# Ensure an app key exists (generate one if the .env placeholder is empty).
if ! grep -q "^APP_KEY=base64:" .env 2>/dev/null; then
    php artisan key:generate --force || true
fi

# Wait for Postgres, then migrate + seed (the Laravel analogue of Flyway-on-boot).
echo "Waiting for database ${DB_HOST:-db}:${DB_PORT:-5432}…"
until php -r "exit(@fsockopen(getenv('DB_HOST')?:'db', (int)(getenv('DB_PORT')?:5432)) ? 0 : 1);" 2>/dev/null; do
    sleep 2
done

php artisan migrate --force --seed || php artisan migrate --force

# Cache config so the resolved values (DB_HOST=db etc., read here via getenv)
# are baked in. This is what makes `php artisan serve` use the container's
# database host instead of falling back to the .env file's 127.0.0.1.
php artisan config:cache || php artisan config:clear || true

# APP_RUNTIME=serve → run the built-in PHP server (HTTP directly, no nginx).
# Default (fpm) → php-fpm behind the nginx "web" service.
if [ "${APP_RUNTIME:-fpm}" = "serve" ]; then
    echo "Serving via php artisan serve on 0.0.0.0:${APP_SERVE_PORT:-8080}"
    exec php artisan serve --host=0.0.0.0 --port="${APP_SERVE_PORT:-8080}"
fi

exec php-fpm
