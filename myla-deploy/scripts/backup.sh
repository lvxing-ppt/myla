#!/bin/bash
BACKUP_DIR="/opt/myla/backup"
DATE=$(date +%Y%m%d_%H%M)
mkdir -p "$BACKUP_DIR"
docker exec myla-mysql mysqldump -uroot -proot --all-databases > "$BACKUP_DIR/myla_$DATE.sql"
find "$BACKUP_DIR" -name "*.sql" -mtime +7 -delete
echo "Backup completed: myla_$DATE.sql"
