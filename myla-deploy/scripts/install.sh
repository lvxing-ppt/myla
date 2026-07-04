#!/bin/bash
set -e

echo "=== MYLA Platform Installer ==="

command -v java >/dev/null 2>&1 || { echo "Java 17 is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Docker is required"; exit 1; }

# Create directories
sudo mkdir -p /opt/myla/{app,data,logs,drivers,backup}
sudo chown -R $USER:$USER /opt/myla

# Start middleware
cd /opt/myla
docker compose up -d mysql redis rabbitmq
echo "Waiting for services..."
sleep 15

# Init database
docker exec myla-mysql mysql -uroot -proot < /opt/myla/sql/V1__init_schema.sql

# Start application
java -jar /opt/myla/app/myla-server.jar --spring.profiles.active=prod > /opt/myla/logs/app.log 2>&1 &

echo "=== Installation Complete ==="
echo "Application: http://localhost:8080"
echo "Default admin: admin / admin123"
